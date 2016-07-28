package org.radargun.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import javax.management.*;

import org.jgroups.*;
import org.jgroups.blocks.*;
import org.jgroups.util.RspList;
import org.jgroups.util.Util;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.*;
import org.radargun.utils.Utils;

/**
 * Plugin measuring the costs of remote gets and puts with JGroups, with regular arguments passed by RadarGun.
 * However, a GET returns a <em>prefabricated</em> value (no cache handling) and a PUT simply invokes the remote call,
 * but doesn't add anything to a hashmap.<p/>
 * The point of this plugin is to measure the overhead of Infinispan's cache handling; it is a base line to the
 * Infinispan plugin. The Infinispan plugin should be slower than the JGroups plugin, but the difference should always
 * be constant, regardless of the cluster size.<p/>
 * Properties, such as the size of the layload for gets, and the number of owners of a key, can be
 * defined in jgroups.properties.
 *
 * Default behavior of puts and gets (mimicking) Infinispan):
 * - A get is sent to the all owners and the call returns after the *first* response has been received. A get which
 *   has the local node included returns immediately (mimicking a local read) and no RPC is sent.
 *
 * - A put is sent to the primary owner P. If P == self --> no-op. P then synchronously sends an update() to
 *   the backup(s) (minus self).
 *
 *

 * @author Bela Ban
 */
@Service(doc = "JGroupsService faking cache operations")
public class JGroups36Service extends ReceiverAdapter implements Lifecycle, Clustered, BasicOperations.Cache {
   protected static Log log = LogFactory.getLog(JGroups36Service.class);

   private static final Method[] METHODS = new Method[7];
   protected static final short GET = 0;
   protected static final short CONTAINS_KEY = 1;
   protected static final short PUT = 2;
   protected static final short GET_AND_PUT = 3;
   protected static final short REMOVE = 4;
   protected static final short GET_AND_REMOVE = 5;
   protected static final short PUT_AND_FORWARD = 6;

   protected JChannel ch;
   protected RpcDispatcher disp;
   protected volatile Address localAddr;
   protected volatile int myRank; // rank of current member in view
   protected volatile List<Address> members = Collections.emptyList();
   protected List<Membership> membershipHistory = new ArrayList<>();

   @Property(doc = "Number of nodes where the writes will be replicated.")
   protected int numOwners = 2;

   @Property(doc = "Controls use of the DONT_BUNDLE flag. Default is true.")
   protected boolean bundle = true;

   @Property(doc = "Controls use of the FC flag. Default is true.")
   protected boolean flowControl = true;

   @Property(doc = "Controls use of the OOB flag. Default is true.")
   protected boolean oob = true;

   @Property(doc = "Controls use of anycasting flag in RequestOptions. Default is true.")
   protected boolean anycasting = true;

   @Property(name = "file", doc = "Configuration file for JGroups.", deprecatedName = "config")
   protected String configFile;

   @Property(doc = "When enabled, a put is sent to the primary which (synchronously) " +
      "replicates it to the backup(s). Otherwise the put is sent to all owners and the call return on the first reply." +
      " Default is true (Infinispan 7.x behavior). Setting this to false will reduce the cost of 4x latency to 2x (faster)")
   protected boolean primaryReplicatesPuts = true;

   protected String name;

   protected volatile Object lastValue = new byte[1000];
   protected RequestOptions getOptions, putOptions, putOptionsWithFilter;
   protected final AtomicInteger localReads = new AtomicInteger(0); // number of local reads (no RPCs)

   static {
      try {
         METHODS[GET] = JGroups36Service.class.getMethod("getFromRemote", Object.class);
         METHODS[CONTAINS_KEY] = JGroups36Service.class.getMethod("containsKeyFromRemote", Object.class);
         METHODS[PUT] = JGroups36Service.class.getMethod("putFromRemote", Object.class, Object.class);
         METHODS[GET_AND_PUT] = JGroups36Service.class.getMethod("getAndPutFromRemote", Object.class, Object.class);
         METHODS[REMOVE] = JGroups36Service.class.getMethod("removeFromRemote", Object.class);
         METHODS[GET_AND_REMOVE] = JGroups36Service.class.getMethod("getAndRemoveFromRemote", Object.class);
         METHODS[PUT_AND_FORWARD] = JGroups36Service.class.getMethod("putFromRemote", Object.class, Object.class, int.class);
      } catch (NoSuchMethodException e) {
         throw new RuntimeException(e);
      }
   }

   public JGroups36Service() {
   }

   public JGroups36Service(String configFile, String name) {
      this.configFile = configFile;
      this.name = name;
   }

   public JGroups36Service configFile(String file) {
      this.configFile = file;
      return this;
   }

   public JGroups36Service name(String name) {
      this.name = name;
      return this;
   }

   public JGroups36Service numOwners(int num) {
      this.numOwners = num;
      return this;
   }

   @ProvidesTrait
   public JGroups36Service getSelf() {
      return this;
   }

   @ProvidesTrait
   public BasicOperations createOperations() {
      return new BasicOperations() {
         @Override
         public <K, V> Cache<K, V> getCache(String cacheName) {
            return JGroups36Service.this;
         }
      };
   }

   @Override
   public void start() {
      this.getOptions = new RequestOptions(ResponseMode.GET_FIRST, 20000, anycasting, null);
      if (oob) {
         getOptions.setFlags(Message.Flag.OOB);
      }
      if (!bundle) {
         getOptions.setFlags(Message.Flag.DONT_BUNDLE);
      }
      if (!flowControl) {
         getOptions.setFlags(Message.Flag.NO_FC);
      }

      this.putOptions = new RequestOptions(ResponseMode.GET_FIRST, 20000, true, null); // uses anycasting
      if (oob) {
         putOptions.setFlags(Message.Flag.OOB);
      }
      if (!bundle) {
         putOptions.setFlags(Message.Flag.DONT_BUNDLE);
      }
      if (!flowControl) {
         putOptions.setFlags(Message.Flag.NO_FC);
      }

      putOptionsWithFilter = new RequestOptions(putOptions).setRspFilter(new FirstNonNullResponse());

      log.debugf("numOwners=%d, config=%s, getOptions=%s, putOptions=%s\n",
         numOwners, configFile, getOptions, putOptions);

      log.info("Loading JGroups form: " + org.jgroups.Version.class.getProtectionDomain().getCodeSource().getLocation());
      log.info("JGroups version: " + org.jgroups.Version.printDescription());

      try {
         ch = new JChannel(configFile).name(name);
         disp = new RpcDispatcher(ch, null, this, this);
         disp.setMethodLookup(id -> METHODS[id]);
         ch.connect("x");
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
      localAddr = ch.getAddress();
      myRank = Util.getRank(ch.getView(), localAddr) - 1;
   }

   @Override
   public void stop() {
      Util.close(ch);
      synchronized (this) {
         membershipHistory.add(Membership.empty());
      }
   }

   @Override
   public boolean isRunning() {
      return ch != null && ch.isConnected();
   }

   public Object getFromRemote(Object key) {
      assert key != null;
      return lastValue;
   }

   public boolean containsKeyFromRemote(Object key) {
      assert this != null && key != null;
      return true;
   }

   public void putFromRemote(Object key, Object value) {
      if (key != null) {
         lastValue = value;
      }
   }

   /**
    * Applies a put() and forwards it to targets
    *
    * @param key
    * @param val
    * @param excludeRank The rank to be excluded from the backups (the originator of the put)
    */
   public void putFromRemote(Object key, Object val, int excludeRank) {
      putFromRemote(key, val);

      // forward to backup owners
      if (excludeRank == -1)
         return;
      List<Address> backupOwners = pickBackups(myRank, excludeRank);
      if (backupOwners == null || backupOwners.isEmpty())
         return;
      if (backupOwners.size() == 1)
         invoke(backupOwners.get(0), new MethodCall(PUT, key, val), putOptions);
      else
         invoke(backupOwners, new MethodCall(PUT, key, val), putOptions);
   }

   public Object getAndPutFromRemote(Object key, Object value) {
      assert key != null;
      Object last = lastValue;
      lastValue = value;
      return last;
   }

   public boolean removeFromRemote(Object key) {
      assert this != null && key != null;
      return true;
   }

   public Object getAndRemoveFromRemote(Object key) {
      assert key != null;
      return lastValue;
   }

   protected Object read(MethodCall methodCall) {
      List<Address> targets = pickReadTargets();
      if (targets == null) { // self was element of the picked members -> local read, no RPC
         localReads.incrementAndGet();
         return lastValue;
      }
      return invoke(targets, methodCall, getOptions).getFirst();
   }

   public Object write(MethodCall methodCall) {
      Collection<Address> targets = pickWriteTargets();
      return invoke(targets, methodCall, putOptionsWithFilter).getFirst();
   }

   @Override
   public Object get(Object key) {
      return read(new MethodCall(GET, key));
   }

   @Override
   public boolean containsKey(Object key) {
      return (Boolean) read(new MethodCall(CONTAINS_KEY, key));
   }

   @Override
   public void put(Object key, Object value) {
      if (this.primaryReplicatesPuts) {
         List<Address> owners = pickTargets(false, false);
         Address primary = owners.remove(0);
         owners.remove(localAddr); // backups shouldn't forward back to us - we already applied the put

         int excludeRank = owners.isEmpty() ? -1 : myRank;
         if (primary.equals(localAddr))
            putFromRemote(key, value, excludeRank);
         else
            invoke(primary, new MethodCall(PUT_AND_FORWARD, key, value, excludeRank), putOptions);
      } else
         write(new MethodCall(PUT, key, value));
   }

   @Override
   public Object getAndPut(Object key, Object value) {
      return write(new MethodCall(GET_AND_PUT, key, value));
   }

   @Override
   public boolean remove(Object key) {
      return (Boolean) write(new MethodCall(REMOVE, key));
   }

   @Override
   public Object getAndRemove(Object key) {
      return write(new MethodCall(GET_AND_REMOVE, key));
   }

   public void clear() {
      lastValue = null;
   }

   public void viewAccepted(View newView) {
      this.members = newView.getMembers();
      this.myRank = Util.getRank(newView, localAddr) - 1;
      ArrayList<Member> mbrs = new ArrayList<>(newView.getMembers().size());
      boolean coord = true;
      for (Address address : newView.getMembers()) {
         mbrs.add(new Member(address.toString(), ch.getAddress().equals(address), coord));
         coord = false;
      }
      synchronized (this) {
         membershipHistory.add(Membership.create(mbrs));
      }
   }

   @Override
   public boolean isCoordinator() {
      View view = ch.getView();
      return view == null || view.getMembers() == null || view.getMembers().isEmpty()
         || ch.getAddress().equals(view.getMembers().get(0));
   }

   @Override
   public synchronized Collection<Member> getMembers() {
      if (membershipHistory.isEmpty()) return null;
      return membershipHistory.get(membershipHistory.size() - 1).members;
   }

   @Override
   public synchronized List<Membership> getMembershipHistory() {
      return new ArrayList<>(membershipHistory);
   }

   // 1-m invocation
   protected RspList<Object> invoke(Collection<Address> targets, MethodCall methodCall, RequestOptions opts) {
      try {
         return disp.callRemoteMethods(targets, methodCall, opts);
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   // 1-1 invocation
   protected Object invoke(Address target, MethodCall methodCall, RequestOptions opts) {
      try {
         return disp.callRemoteMethod(target, methodCall, opts);
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }


   /**
    * Picks a random primary plus numOwners-1 backup members from the membership
    *
    * @return The list of primary and backup members, or null if self was element of that list (local reads)
    */
   protected List<Address> pickReadTargets() {
      return pickTargets(true, false);
   }

   /**
    * Picks a random primary and numOwners-1 backups, but removes self. So if we pick {A,B} (self=A), then the RPC will
    * only go to B.
    *
    * @return
    */
   protected List<Address> pickWriteTargets() {
      return pickTargets(false, true); // exclude self
   }


   /**
    * Picks numOwners targets in range [i .. i+numOwners-1] where i is a random index
    *
    * @return A list of members (primary plus backup(s)), or null if returnNullOnSelfInclusion is true and self is in
    * the list
    */
   protected List<Address> pickTargets(boolean returnNullOnSelfInclusion, boolean skipSelf) {
      List<Address> mbrs = this.members;
      int size = mbrs.size();
      int startIndex = ThreadLocalRandom.current().nextInt(size);
      int numTargets = Math.min(numOwners, size);

      List<Address> targets = new ArrayList<>(numTargets);
      for (int i = 0; i < numTargets; i++) {
         int index = (startIndex + i) % size;
         if (index == myRank) {
            if (returnNullOnSelfInclusion)
               return null;
            if (skipSelf)
               continue;
         }
         Address target = mbrs.get(index);
         targets.add(target); // we cannot have dupes because numTargets cannot be > size (due to the min() above)
      }
      return targets;
   }

   /**
    * Picks backup owners, based on a starting index
    *
    * @param primaryRank The rank of the primary in the current view. Start with (primaryRank+1) % size
    * @param excludeRank Exclude primary if true
    * @return
    */
   protected List<Address> pickBackups(int primaryRank, int excludeRank) {
      List<Address> mbrs = this.members;
      int size = mbrs.size();
      int startIndex = primaryRank + 1;
      int numTargets = Math.min(numOwners - 1, size);

      List<Address> targets = new ArrayList<>(numTargets);
      for (int i = 0; i < numTargets; i++) {
         int index = (startIndex + i) % size;
         if (index == excludeRank)
            continue;
         Address target = mbrs.get(index);
         targets.add(target); // we cannot have dupes because numTargets cannot be > size (due to the min() above)
      }
      return targets;
   }


   @ProvidesTrait
   ConfigurationProvider getConfigurationProvider() {
      return new ConfigurationProvider() {
         @Override
         public Map<String, Properties> getNormalizedConfigs() {
            return Collections.singletonMap("jgroups", dumpProperties());
         }

         @Override
         public Map<String, byte[]> getOriginalConfigs() {
            InputStream stream = null;
            try {
               stream = getClass().getResourceAsStream(configFile);
               if (stream == null) {
                  stream = new FileInputStream(configFile);
               }
               return Collections.singletonMap(configFile, Utils.readAsBytes(stream));
            } catch (IOException e) {
               log.error("Cannot read configuration file " + configFile, e);
               return Collections.EMPTY_MAP;
            } finally {
               Utils.close(stream);
            }
         }
      };
   }

   protected Properties dumpProperties() {
      Properties p = new Properties();
      try {
         MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
         String objName = String.format("jboss.infinispan:type=protocol,cluster=\"%s\",protocol=*", ch.getClusterName());
         Set<ObjectInstance> beanObjs = mbeanServer.queryMBeans(new ObjectName(objName), null);
         if (beanObjs.isEmpty()) {
            log.error("no JGroups protocols found");
            return p;
         }
         for (ObjectInstance beanObj : beanObjs) {
            ObjectName protocolObjectName = beanObj.getObjectName();
            MBeanInfo protocolBean = mbeanServer.getMBeanInfo(protocolObjectName);
            String protocolName = protocolObjectName.getKeyProperty("protocol");
            for (MBeanAttributeInfo info : protocolBean.getAttributes()) {
               String propName = info.getName();
               Object propValue = mbeanServer.getAttribute(protocolObjectName, propName);
               p.setProperty(protocolName + "." + propName, propValue == null ? "null" : propValue.toString());
            }
         }
         return p;
      } catch (Exception e) {
         log.error("Error while dumping JGroups config as properties", e);
         return p;
      }
   }

   /**
    * Terminates after the first non-null response
    */
   protected static class FirstNonNullResponse implements RspFilter {
      protected boolean receivedNonNullRsp;

      public boolean isAcceptable(Object response, Address sender) {
         if (response != null) {
            receivedNonNullRsp = true;
            return true;
         }
         return false;
      }

      public boolean needMoreResponses() {
         return !receivedNonNullRsp;
      }
   }
}

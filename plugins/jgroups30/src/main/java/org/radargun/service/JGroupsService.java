package org.radargun.service;

import org.jgroups.*;
import org.jgroups.blocks.*;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.jgroups.util.RspList;
import org.jgroups.util.Util;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.Clustered;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


/**
 * Plugin measuring the costs of remote gets and puts with JGroups, with regular arguments passed by RadarGun.
 * However, a GET returns a <em>prefabricated</em> value (no cache handling) and a PUT simply invokes the remote call,
 * but doesn't add anything to a hashmap.<p/>
 * The point of this plugin is to measure the overhead of Infinispan's cache handling; it is a base line to the
 * Infinispan plugin. The Infinispan plugin should be slower than the JGroups plugin, but the difference should always
 * be constant, regardless of the cluster size.<p/>
 * Properties, such as the size of the layload for gets, and the number of owners of a key, can be
 * defined in jgroups.properties.
 * @author Bela Ban
 */
@Service(doc = "JGroups faking cache operations")
public class JGroupsService extends ReceiverAdapter implements Lifecycle, Clustered, BasicOperations.Cache {
   protected static Log log = LogFactory.getLog(JGroupsService.class);

   private static final Method[] METHODS = new Method[6];
   protected static final short GET = 0;
   protected static final short CONTAINS_KEY = 1;
   protected static final short PUT = 2;
   protected static final short GET_AND_PUT = 3;
   protected static final short REMOVE = 4;
   protected static final short GET_AND_REMOVE = 5;

   protected JChannel ch;
   protected RpcDispatcher disp;

   protected volatile boolean started = false;
   protected volatile Address localAddr;
   protected volatile List<Address> members = Collections.emptyList();
   protected List<Membership> membershipHistory = new ArrayList<Membership>();

   public enum SelfRequests {
      execute,
      exclude,
      noop
   }

   @Property(doc = "Number of nodes where the writes will be replicated.")
   private int numOwners = 2;
   @Property(doc = "What to do with requests directed on ourselves. Variants are 'execute', 'exclude' and 'noop'. Default is execute.")
   private SelfRequests selfRequests = SelfRequests.execute;
   @Property(doc = "Controls use of the DONT_BUNDLE flag. Default is false.")
   private boolean bundle;
   @Property(doc = "Controls use of the FC flag. Default is false.")
   private boolean flowControl;
   @Property(doc = "If true, reads are executed on all owners using ResponseMode GET_FIRST. Otherwise it just randomly picks one node for reading. Default is false.")
   private boolean getFirst;
   @Property(doc = "Controls use of the OOB flag. Default is false.")
   private boolean oob;
   @Property(doc = "Controls use of anycasting flag in RequestOptions. Default is false.")
   private boolean anycasting;

   @Property(name = "file", doc = "Configuration file for JGroups.", deprecatedName = "config")
   protected String configFile;

   private boolean excludeSelfRequests;
   private boolean noopSelfRequests;
   private volatile Object lastValue = null;

   static {
      try {
         METHODS[GET] = JGroupsService.class.getMethod("getFromRemote", Object.class);
         METHODS[CONTAINS_KEY] = JGroupsService.class.getMethod("containsKeyFromRemote", Object.class);
         METHODS[PUT] = JGroupsService.class.getMethod("putFromRemote", Object.class, Object.class);
         METHODS[GET_AND_PUT] = JGroupsService.class.getMethod("getAndPutFromRemote", Object.class, Object.class);
         METHODS[REMOVE] = JGroupsService.class.getMethod("removeFromRemote", Object.class);
         METHODS[GET_AND_REMOVE] = JGroupsService.class.getMethod("getAndRemoveFromRemote", Object.class);
      } catch (NoSuchMethodException e) {
         throw new RuntimeException(e);
      }
   }

   @ProvidesTrait
   public JGroupsService getSelf() {
      return this;
   }

   @ProvidesTrait
   public BasicOperations createOperations() {
      return new BasicOperations() {
         @Override
         public <K, V> Cache<K, V> getCache(String cacheName) {
            return JGroupsService.this;
         }
      };
   }

   @Override
   public void start() {
      excludeSelfRequests = selfRequests == SelfRequests.exclude;
      noopSelfRequests = selfRequests == SelfRequests.noop;

      log.debug("numOwners=" + numOwners + ", selfRequests=" + selfRequests + ", config=" + configFile);

      if (!started) {
         log.info("Loading JGroups form: " + org.jgroups.Version.class.getProtectionDomain().getCodeSource().getLocation());
         log.info("JGroups version: " + org.jgroups.Version.printDescription());

         try {
            ch = new JChannel(configFile);

            disp = new RpcDispatcher(ch, null, this, this);
            disp.setMethodLookup(new MethodLookup() {
               public Method findMethod(short id) {
                  return METHODS[id];
               }
            });

            ch.connect("x");
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
         localAddr = ch.getAddress();

         started = true;
      }
   }

   @Override
   public void stop() {
      Util.close(ch);
      synchronized (this) {
         membershipHistory.add(Membership.empty());
      }
      started = false;
   }

   @Override
   public boolean isRunning() {
      return ch != null && ch.isConnected();
   }

   public Object getFromRemote(Object key) {
      return lastValue;
   }

   public boolean containsKeyFromRemote(Object key) {
      return true;
   }

   public void putFromRemote(Object key, Object value) {
      lastValue = value;
   }

   public Object getAndPutFromRemote(Object key, Object value) {
      Object last = lastValue;
      lastValue = value;
      return last;
   }

   public boolean removeFromRemote(Object key) {
      return true;
   }

   public Object getAndRemoveFromRemote(Object key) {
      return lastValue;
   }

   private Object read(MethodCall methodCall) {
      RequestOptions getOptions = new RequestOptions(getFirst ? ResponseMode.GET_FIRST : ResponseMode.GET_ALL, 20000, false, null);

      if (oob) {
         getOptions.setFlags(Message.Flag.OOB);
      }
      if (!bundle) {
         getOptions.setFlags(Message.Flag.DONT_BUNDLE);
      }
      if (!flowControl) {
         getOptions.setFlags(Message.Flag.NO_FC);
      }
      getOptions.setAnycasting(anycasting);

      try {
         if (getFirst) {
            List<Address> targets = pickReadTargets();
            if (targets == null) {
               return lastValue;
            }
            RspList<Object> responses = disp.callRemoteMethods(targets, methodCall, getOptions);
            return responses.getFirst();
         } else {
            Address target = pickReadTarget();

            // we're simulating picking ourselves, which returns the data directly from the local cache - no RPC involved
            if (target == null) {
               return lastValue;
            }

            return disp.callRemoteMethod(target, methodCall, getOptions);
         }
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   public Object write(MethodCall methodCall) {
      RequestOptions putOptions = new RequestOptions(ResponseMode.GET_ALL, 20000, true, null); // uses anycasting

      if (oob) {
         putOptions.setFlags(Message.Flag.OOB);
      }
      if (!bundle) {
         putOptions.setFlags(Message.Flag.DONT_BUNDLE);
      }
      if (!flowControl) {
         putOptions.setFlags(Message.Flag.NO_FC);
      }

      Collection<Address> targets = pickWriteTargets();
      try {
         return disp.callRemoteMethods(targets, methodCall, putOptions).getFirst();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public Object get(Object key) {
      return read(new MethodCall(GET, new Object[]{key}));
   }

   @Override
   public boolean containsKey(Object key) {
      return (Boolean) read(new MethodCall(CONTAINS_KEY, new Object[]{key}));
   }

   @Override
   public void put(Object key, Object value) {
      write(new MethodCall(PUT, new Object[]{key, value}));
   }

   @Override
   public Object getAndPut(Object key, Object value) {
      return write(new MethodCall(GET_AND_PUT, new Object[]{key, value}));
   }

   @Override
   public boolean remove(Object key) {
      return (Boolean) write(new MethodCall(REMOVE, new Object[] { key }));
   }

   @Override
   public Object getAndRemove(Object key) {
      return write(new MethodCall(GET_AND_REMOVE, new Object[] { key }));
   }

   public void clear() {
      lastValue = null;
   }

   public void viewAccepted(View newView) {
      ArrayList<Address> members = new ArrayList<Address>(newView.getMembers());
      // put the local address at the end of the list
      Collections.rotate(members, members.size() - members.indexOf(ch.getAddress()));
      this.members = members;
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
      if (view == null || view.getMembers() == null || view.getMembers().isEmpty()) return true;
      return ch.getAddress().equals(view.getMembers().get(0));
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

   private Address pickReadTarget() {
      List<Address> members = this.members; // grab reference
      int size = excludeSelfRequests ? members.size() - 1 : members.size();
      int index = ThreadLocalRandom.current().nextInt(size);

      // self also has the keys for the previous numOwners - 1 nodes
      if (noopSelfRequests && index >= members.size() - numOwners)
         return null;
      
      return members.get(index);
   }

   private List<Address> pickReadTargets() {
      List<Address> members = this.members;
      int size = excludeSelfRequests ? members.size() - 1 : members.size();
      int startIndex = ThreadLocalRandom.current().nextInt(size);

      // self also has the keys for the previous numOwners - 1 nodes
      if (noopSelfRequests && startIndex >= members.size() - numOwners)
         return null;

      int numTargets = Math.min(numOwners, excludeSelfRequests ? members.size() - 1 : members.size());
      List<Address> targets = new ArrayList<Address>(numTargets);
      for (int i = 0; i < numTargets; ++i) {
         targets.add(members.get((startIndex + i) % size));
      }
      return targets;
   }

   private Collection<Address> pickWriteTargets() {
      List<Address> members = this.members;
      int size = excludeSelfRequests ? members.size() - 1 : members.size();
      int startIndex = ThreadLocalRandom.current().nextInt(size);

      Collection<Address> targets = new ArrayList<Address>(numOwners);
      for (int i = 0; i < numOwners; i++) {
         int newIndex = (startIndex + i) % size;

         if (noopSelfRequests && newIndex == members.size() - 1)
            continue;

         targets.add(members.get(newIndex));
      }
      return targets;
   }

}

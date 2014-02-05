package org.radargun.cachewrappers;

import javax.transaction.TransactionManager;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.Version;
import org.jgroups.View;
import org.jgroups.blocks.MethodCall;
import org.jgroups.blocks.MethodLookup;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.ResponseMode;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.jgroups.util.RspList;
import org.jgroups.util.Util;
import org.radargun.CacheWrapper;
import org.radargun.utils.TypedProperties;


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
public class JGroupsWrapper extends ReceiverAdapter implements CacheWrapper {
   protected static Log log = LogFactory.getLog(JGroupsWrapper.class);

   private static final Method[] METHODS = new Method[3];
   protected static final short GET = 0;
   protected static final short PUT = 1;
   protected static final short REMOVE = 2;

   protected JChannel ch;
   protected RpcDispatcher disp;
   protected TransactionManager tm;

   protected volatile boolean started = false;
   protected volatile Address localAddr;
   protected volatile List<Address> members = Collections.emptyList();

   private int numOwners;
   private boolean excludeSelfRequests;
   private boolean noopSelfRequests;
   private boolean bundle;
   private boolean flowControl;
   private boolean getFirst;
   private boolean oob;
   private boolean anycasting;
   private volatile Object lastValue = null;

   static {
      try {
         METHODS[GET] = JGroupsWrapper.class.getMethod("getFromRemote", Object.class);
         METHODS[PUT] = JGroupsWrapper.class.getMethod("putFromRemote", Object.class, Object.class);
         METHODS[REMOVE] = JGroupsWrapper.class.getMethod("removeFromRemote", Object.class);
      } catch (NoSuchMethodException e) {
         throw new RuntimeException(e);
      }
   }


   public void setUp(String configName, boolean isLocal, int nodeIndex, TypedProperties confAttributes) throws Exception {
      numOwners = confAttributes.getIntProperty("numOwners", 2);
      String selfRequests = confAttributes.getProperty("selfRequests", "");
      excludeSelfRequests = "exclude".equalsIgnoreCase(selfRequests);
      noopSelfRequests = "noop".equals(selfRequests);
      bundle = confAttributes.getBooleanProperty("bundle", false);
      flowControl = confAttributes.getBooleanProperty("flowControl", false);
      getFirst = confAttributes.getBooleanProperty("getFirst", false);
      oob = confAttributes.getBooleanProperty("oob", false);
      anycasting = confAttributes.getBooleanProperty("anycasting", false);
      String configFile = confAttributes.getProperty("file", configName);

      log.debug("numOwners=" + numOwners + ", selfRequests=" + selfRequests + ", config=" + configFile);

      if (!started) {
         log.info("Loading JGroups form: " + org.jgroups.Version.class.getProtectionDomain().getCodeSource().getLocation());
         log.info("JGroups version: " + org.jgroups.Version.printDescription());

         ch = new JChannel(configFile);

         disp = new RpcDispatcher(ch, null, this, this);
         disp.setMethodLookup(new MethodLookup() {
            public Method findMethod(short id) {
               return METHODS[id];
            }
         });

         ch.connect("x");
         localAddr = ch.getAddress();

         started = true;
      }
   }

   public void tearDown() throws Exception {
      Util.close(ch);
      started = false;
   }

   @Override
   public boolean isRunning() {
      return ch.isConnected();
   }

   public void putFromRemote(Object key, Object value) throws Exception {
      lastValue = value;
   }

   public Object getFromRemote(Object key) throws Exception {
      return lastValue;
   }

   public Object removeFromRemote(Object key) throws Exception {
      return lastValue;
   }

   public void put(String bucket, Object key, Object value) throws Exception {
      Object[] putArgs = new Object[]{key, value};
      MethodCall putCall = new MethodCall(PUT, putArgs);
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

      Collection<Address> targets = pickPutTargets();
      disp.callRemoteMethods(targets, putCall, putOptions);
   }

   public Object get(String bucket, Object key) throws Exception {
      Object[] getArgs = new Object[]{key};
      MethodCall getCall = new MethodCall(GET, getArgs);
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

      if (getFirst) {
         List<Address> targets = pickGetTargets();
         if (targets == null) {
            return lastValue;
         }
         RspList<Object> responses = disp.callRemoteMethods(targets, getCall, getOptions);
         return responses.getFirst();
      } else {
         Address target = pickGetTarget();

         // we're simulating picking ourselves, which returns the data directly from the local cache - no RPC involved
         if (target == null) {
            return lastValue;
         }

         return disp.callRemoteMethod(target, getCall, getOptions);
      }
   }
   
   public Object remove(String bucket, Object key) throws Exception {
      Object[] removeArgs = new Object[]{key};
      MethodCall removeCall = new MethodCall(REMOVE, removeArgs);
      RequestOptions removeOptions = new RequestOptions(ResponseMode.GET_ALL, 20000, true, null); // uses anycasting

      if (oob) {
         removeOptions.setFlags(Message.Flag.OOB);
      }
      if (!bundle) {
         removeOptions.setFlags(Message.Flag.DONT_BUNDLE);
      }
      if (!flowControl) {
         removeOptions.setFlags(Message.Flag.NO_FC);
      }

      Collection<Address> targets = pickPutTargets();
      RspList<Object> responses = disp.callRemoteMethods(targets, removeCall, removeOptions);
      return responses.getFirst();
   }        

   public void clear(boolean local) throws Exception {
      lastValue = null;
   }

   public void viewAccepted(View newView) {
      ArrayList<Address> members = new ArrayList<Address>(newView.getMembers());
      // put the local address at the end of the list
      Collections.rotate(members, members.size() - members.indexOf(ch.getAddress()));
      this.members = members;
   }

   public int getNumMembers() {
      View view = ch.getView();
      return view != null ? view.size() : 0;
   }

   public String getInfo() {
      StringBuilder sb = new StringBuilder();
      sb.append("view=" + ch.getViewAsString()).append(" [version=").append(Version.printVersion()).append("]");
      return sb.toString();
   }

   public Object getReplicatedData(String bucket, String key) throws Exception {
      return get(bucket, key);
   }

   @Override
   public boolean isTransactional(String bucket) {
      return tm != null;
   }

   public void startTransaction() {
      if (tm == null) return;
      try {
         tm.begin();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   public void endTransaction(boolean successful) {
      if (tm == null) return;
      try {
         if (successful)
            tm.commit();
         else
            tm.rollback();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public int getLocalSize() {
      return -1; // TODO: implement me
   }
   
   @Override
   public int getTotalSize() {
      return -1; // TODO: implement me
   }

   private Address pickGetTarget() {
      List<Address> members = this.members; // grab reference
      int size = excludeSelfRequests ? members.size() - 1 : members.size();
      int index = ThreadLocalRandom.current().nextInt(size);

      // self also has the keys for the previous numOwners - 1 nodes
      if (noopSelfRequests && index >= members.size() - numOwners)
         return null;
      
      return members.get(index);
   }

   private List<Address> pickGetTargets() {
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

   private Collection<Address> pickPutTargets() {
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

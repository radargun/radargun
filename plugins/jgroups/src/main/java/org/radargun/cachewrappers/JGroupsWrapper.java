package org.radargun.cachewrappers;

import org.jgroups.*;
import org.jgroups.blocks.*;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.jgroups.util.Util;
import org.radargun.CacheWrapper;
import org.radargun.utils.TypedProperties;

import com.arjuna.ats.jta.exceptions.NotImplementedException;

import javax.transaction.TransactionManager;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;


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
   private static Log log = LogFactory.getLog(JGroupsWrapper.class);
   public static Random random = new Random();

   private static final String NUM_OWNERS = "num_owners";
   private static final String ATTR_SIZE = "attr_size";
   private static final String SELF_REQUESTS = "self_requests";
   private static final String JGROUPS_CONFIG = "jgroups_config";

   private static final Method[] METHODS = new Method[2];
   protected static final short GET = 0;
   protected static final short PUT = 1;

   protected JChannel ch;
   protected RpcDispatcher disp;
   protected TransactionManager tm;

   protected volatile boolean started = false;
   protected volatile Address local_addr;
   protected volatile List<Address> members = Collections.emptyList();

   private int num_owners;
   private byte[] get_rsp;
   private boolean exclude_self_requests;
   private boolean noop_self_requests;

   static {
      try {
         METHODS[GET] = JGroupsWrapper.class.getMethod("_get", Object.class);
         METHODS[PUT] = JGroupsWrapper.class.getMethod("_put", Object.class, Object.class);
      } catch (NoSuchMethodException e) {
         throw new RuntimeException(e);
      }
   }


   public void setUp(String config, boolean isLocal, int nodeIndex, TypedProperties confAttributes) throws Exception {
      InputStream in = Util.getResourceAsStream(config, JGroupsWrapper.class);
      Properties props = new Properties();
      props.load(in);
      num_owners = Integer.parseInt(props.getProperty(NUM_OWNERS, "2"));

      int attrSize = Integer.parseInt(props.getProperty(ATTR_SIZE, "1000"));
      get_rsp = new byte[attrSize];

      String self_requests = props.getProperty(SELF_REQUESTS, "").toLowerCase();
      exclude_self_requests = "exclude".equals(self_requests);
      noop_self_requests = "noop".equals(self_requests);

      String jgroupsConfig = props.getProperty(JGROUPS_CONFIG);
      log.debug("numOwners=" + num_owners + ", self_requests=" + self_requests + ", jgroupsConfig=" + jgroupsConfig);

      if (!started) {
         log.info("Loading JGroups form: " + org.jgroups.Version.class.getProtectionDomain().getCodeSource().getLocation());
         log.info("JGroups version: " + org.jgroups.Version.printDescription());

         ch = new JChannel(jgroupsConfig);

         disp = new RpcDispatcher(ch, null, this, this);
         disp.setMethodLookup(new MethodLookup() {
            public Method findMethod(short id) {
               return METHODS[id];
            }
         });

         ch.connect("x");
         local_addr = ch.getAddress();

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

   public void _put(Object key, Object value) throws Exception {
      ;
   }

   public Object _get(Object key) throws Exception {
      return get_rsp;
   }

   public void put(String bucket, Object key, Object value) throws Exception {
      Object[] put_args = new Object[]{key, value};
      MethodCall put_call = new MethodCall(PUT, put_args);
      RequestOptions put_options = new RequestOptions(ResponseMode.GET_ALL, 20000, true, null); // uses anycasting

      put_options.setFlags(Message.DONT_BUNDLE, Message.NO_FC);

      Collection<Address> targets = pickPutTargets();
      disp.callRemoteMethods(targets, put_call, put_options);
   }

   public Object get(String bucket, Object key) throws Exception {
      Object[] get_args = new Object[]{key};
      MethodCall get_call = new MethodCall(GET, get_args);
      RequestOptions get_options = new RequestOptions(ResponseMode.GET_ALL, 20000, false, null);

      get_options.setFlags(Message.DONT_BUNDLE, Message.NO_FC);

      Address target = pickGetTarget();

      // we're simulating picking ourselves, which returns the data directly from the local cache - no RPC involved
      if (target == null)
         return get_rsp;
      
      try {
         return disp.callRemoteMethod(target, get_call, get_options);
      } catch (Throwable t) {
         throw new Exception(t);
      }
   }
   
   public Object remove(String bucket, Object key) throws Exception {
      throw new NotImplementedException();
   }   

   public void empty() throws Exception {
      ; // no-op
   }


   public void viewAccepted(View new_view) {
      ArrayList<Address> members = new ArrayList<Address>(new_view.getMembers());
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
      int size = exclude_self_requests ? members.size() - 1 : members.size();
      int index = random.nextInt(size);

      // self also has the keys for the previous num_owners - 1 nodes
      if (noop_self_requests && index >= members.size() - num_owners)
         return null;
      
      return members.get(index);
   }

   private Collection<Address> pickPutTargets() {
      int size = exclude_self_requests ? members.size() - 1 : members.size();
      int startIndex = random.nextInt(size);

      Collection<Address> targets = new ArrayList<Address>(num_owners);
      for (int i = 0; i < num_owners; i++) {
         int new_index = (startIndex + i) % size;

         if (noop_self_requests && new_index == members.size() - 1)
            continue;

         targets.add(members.get(new_index));
      }
      return targets;
   }

}

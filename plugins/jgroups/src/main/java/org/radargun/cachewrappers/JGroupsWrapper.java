package org.radargun.cachewrappers;

import org.jgroups.*;
import org.jgroups.blocks.*;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.jgroups.util.Util;
import org.radargun.CacheWrapper;
import org.radargun.utils.TypedProperties;

import javax.transaction.TransactionManager;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;


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
   protected JChannel ch;
   protected Address local_addr;
   protected RpcDispatcher disp;
   protected TransactionManager tm;
   protected boolean started = false;
   protected final List<Address> members = new ArrayList<Address>();

   private static byte[] GET_RSP;
   private static int num_owners;
   private static boolean exclude_self_for_gets = false;

   private static final Method[] METHODS = new Method[5];
   private static final String PROP_FILE = "jgroups.properties";
   private static final String NUM_OWNERS = "num_owners";
   private static final String ATTR_SIZE = "attr_size";
   private static final String EXCLUDE_SELF_FOR_GETS = "exclude_self_for_gets";

   protected static final short GET = 1;
   protected static final short PUT = 2;

   static {
      try {
         InputStream in = Util.getResourceAsStream(PROP_FILE, JGroupsWrapper.class);
         Properties props = new Properties();
         props.load(in);
         num_owners = Integer.parseInt(props.getProperty(NUM_OWNERS, "2"));
         int size = Integer.parseInt(props.getProperty(ATTR_SIZE, "1000"));
         GET_RSP = new byte[size];
         exclude_self_for_gets = Boolean.parseBoolean(props.getProperty(EXCLUDE_SELF_FOR_GETS, "false"));
         System.out.println("numOwners=" + num_owners + ", attrSize=" + size + ", exclude_self_for_gets=" + exclude_self_for_gets);
      } catch (Exception e) {
         throw new RuntimeException(e);
      }


      try {
         METHODS[GET] = JGroupsWrapper.class.getMethod("_get", Object.class);
         METHODS[PUT] = JGroupsWrapper.class.getMethod("_put", Object.class, Object.class);
      } catch (NoSuchMethodException e) {
         throw new RuntimeException(e);
      }
   }


   public void setUp(String config, boolean isLocal, int nodeIndex, TypedProperties confAttributes) throws Exception {
      if (!started) {
         log.info("Loading JGroups form: " + org.jgroups.Version.class.getProtectionDomain().getCodeSource().getLocation());
         log.info("JGroups version: " + org.jgroups.Version.printDescription());
         ch = new JChannel(config);
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

   public static void _put(Object key, Object value) throws Exception {
      ;
   }

   public static Object _get(Object key) throws Exception {
      return GET_RSP;
   }

   public void put(String bucket, Object key, Object value) throws Exception {
      Object[] put_args = new Object[]{key, value};
      MethodCall put_call = new MethodCall(PUT, put_args);
      RequestOptions put_options = new RequestOptions(Request.GET_ALL, 20000, true, null); // uses anycasting

      byte flags = 0;
      flags = Util.setFlag(flags, Message.DONT_BUNDLE);
      flags = Util.setFlag(flags, Message.NO_FC);
      put_options.setFlags(flags);

      Collection<Address> targets = pickAnycastTargets();
      disp.callRemoteMethods(targets, put_call, put_options);
   }

   public Object get(String bucket, Object key) throws Exception {
      Object[] get_args = new Object[]{key};
      MethodCall get_call = new MethodCall(GET, get_args);
      RequestOptions get_options = new RequestOptions(Request.GET_ALL, 20000, false, null);

      byte flags = 0;
      flags = Util.setFlag(flags, Message.DONT_BUNDLE);
      flags = Util.setFlag(flags, Message.NO_FC);
      get_options.setFlags(flags);

      Address target = pickTarget();

      // we're simulating picking ourself, which returns the data directly from the local cache - no RPC involved
      if (local_addr.equals(target))
         return GET_RSP;
      try {
         return disp.callRemoteMethod(target, get_call, get_options);
      } catch (Throwable t) {
         throw new Exception(t);
      }
   }

   public void empty() throws Exception {
      ; // no-op
   }


   public void viewAccepted(View new_view) {
      members.clear();
      members.addAll(new_view.getMembers());
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

   public int size() {
      return 0;
   }

   private Address pickTarget() {
      if (!exclude_self_for_gets)
         return (Address) Util.pickRandomElement(members);
      else {
         int index = members.indexOf(local_addr);
         int new_index = (index + 1) % members.size();
         return members.get(new_index);
      }
   }

   private Collection<Address> pickAnycastTargets() {
      Collection<Address> anycast_targets = new ArrayList<Address>(num_owners);
      int index = members.indexOf(local_addr);
      for (int i = index + 1; i < index + 1 + num_owners; i++) {
         int new_index = i % members.size();
         anycast_targets.add(members.get(new_index));
      }
      return anycast_targets;
   }
}

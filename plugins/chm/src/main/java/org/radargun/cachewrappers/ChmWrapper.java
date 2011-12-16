package org.radargun.cachewrappers;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.NamedCache;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.utils.TypedProperties;


import javax.transaction.Transaction;
import java.util.concurrent.ConcurrentHashMap;

public class ChmWrapper implements CacheWrapper {

   public final ConcurrentHashMap chm = new ConcurrentHashMap();

   @Override
   public void setUp(String configuration, boolean isLocal, int nodeIndex, TypedProperties confAttributes) throws Exception {
   }

   public void tearDown() throws Exception {
   }

   @Override
   public void put(String bucket, Object key, Object value) throws Exception {
      chm.put(key, value);
   }

   @Override
   public Object get(String bucket, Object key) throws Exception {
      return chm.get(key);
   }

   public void empty() throws Exception {
      chm.clear();
   }

   public int getNumMembers() {
      return 1;
   }

   public String getInfo() {
      return "Concurrent hash map wrapper";
   }

   @Override
   public Object getReplicatedData(String bucket, String key) throws Exception {
      return null;
   }

   public void startTransaction() {
      throw new IllegalStateException("This is not transactional");
   }

   public void endTransaction(boolean successful) {
   }

   @Override
   public int size() {
      return 0;  // TODO: Customise this generated block
   }
}

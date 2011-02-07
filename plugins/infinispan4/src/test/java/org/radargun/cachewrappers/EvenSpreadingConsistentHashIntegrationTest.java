package org.radargun.cachewrappers;

import org.infinispan.config.Configuration;
import org.infinispan.test.MultipleCacheManagersTest;
import org.infinispan.test.TestingUtil;
import org.radargun.stressors.ObjectKey;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test
public class EvenSpreadingConsistentHashIntegrationTest extends MultipleCacheManagersTest {

   private int numOwners = 2;
   private int keyCount = 5;
   private int threadCount = 2;

   @Override
   protected void createCacheManagers() throws Throwable {
      Configuration config = getDefaultClusteredConfig(Configuration.CacheMode.DIST_SYNC);
      config.setNumOwners(numOwners);
      config.setRehashEnabled(false);
      config.setL1CacheEnabled(false);
      config.setConsistentHashClass(EvenSpreadingConsistentHash.class.getName());
      addClusterEnabledCacheManager(config);
      addClusterEnabledCacheManager(config);
      addClusterEnabledCacheManager(config);
      cache(0);
      log.info("Address 0 = " + advancedCache(0).getRpcManager().getAddress());
      log.info("Address 1 = " + advancedCache(1).getRpcManager().getAddress());
      log.info("Address 2 = " + advancedCache(2).getRpcManager().getAddress());
      cache(1);
      cache(2);
      waitForClusterToForm();
      init(0);
      init(1);
      init(2);
   }

   private void init(int index) {
      EvenSpreadingConsistentHash even = (EvenSpreadingConsistentHash) advancedCache(index).getDistributionManager().getConsistentHash();
      even.init(threadCount, keyCount);
   }


   @Test(enabled = false)
   public void testSimple() {
      for (int i = 0; i < keyCount; i++) {
         ObjectKey key = new ObjectKey(0, 0, i);
         cache(0).put(key, "0v"+i);
         int count = count(key, 0) + count(key, 1) + count(key, 2);
         if (count != numOwners) {
            System.out.println("count 0 " + key + " " + count(key, 0));
            System.out.println("count 1 "+ key + " " + count(key, 1));
            System.out.println("count 2 " + key + " " + count(key, 2));
            System.out.println(TestingUtil.printCache(this.cache(0)));
            System.out.println(TestingUtil.printCache(this.cache(1)));
            System.out.println(TestingUtil.printCache(this.cache(2)));
            assert false;
         }
      }

      assertEquals(numOwners * keyCount, countKeys(0) + countKeys(1) + countKeys(2));


      for (int i = 0; i < keyCount; i++) {
         assertEquals(cache(0).get(new ObjectKey(0,0,i)), "0v"+i);
         assertEquals(cache(1).get(new ObjectKey(0,0,i)), "0v"+i);
         assertEquals(cache(2).get(new ObjectKey(0,0,i)), "0v"+i);
      }
   }

   public void testAllNodes() {
      int nodeCount = caches().size();
      for (int i = 0; i < nodeCount; i++) {
         for (int j = 0; j < threadCount; j++) {
            for (int k = 0; k < keyCount; k++) {
               ObjectKey key = new ObjectKey(i, j, k);
               cache(i).put(key, i + "_" + j + "_" + k);
               int count = count(key, 0) + count(key, 1) + count(key, 2);
               if (count != numOwners) {
                  assert false;
               }

            }
         }
      }
      System.out.println("countKeys(0) = " + countKeys(0));
      System.out.println("countKeys(1) = " + countKeys(1));
      System.out.println("countKeys(2) = " + countKeys(2));

      assertDiff(0, 1);
      assertDiff(1, 2);
      assertDiff(2, 0);

      int expected = numOwners * keyCount * threadCount * nodeCount;
      System.out.println("expected = " + expected);
      assertEquals(countKeys(0) + countKeys(1) + countKeys(2), expected);
   }

   private void assertDiff(int c1, int c2) {
      int c1Size = countKeys(c1);
      int c2Size = countKeys(c2);
      assert Math.abs(c1Size - c2Size) <= 1 : "c" + c1 + ".size==" + c1Size + ", c" + c2 + ".size==" + c2Size;
   }

   private int countKeys(int cache) {
      return cache(cache).keySet().size();
   }

   int count(Object key, int cache) {
      if (advancedCache(cache).getDataContainer().containsKey(key)) return 1;
      return 0;
   }
}

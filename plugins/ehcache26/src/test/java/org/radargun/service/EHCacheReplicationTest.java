package org.radargun.service;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.Ehcache;

/**
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Test (enabled = false)
public class EHCacheReplicationTest
{
   String cfgFile = "/path/to/file.xml";
   @BeforeMethod
   public void setUp()
   {
      System.setProperty("bind.address","127.0.0.1");//bind address referenced from config file
   }


   public void testSyncReplication() throws Exception
   {
      Ehcache cache1;
      Ehcache cache2;



      CacheManager c1 = new CacheManager(cfgFile);
      CacheManager c2 = new CacheManager(cfgFile);


      cache1 = c1.getCache("cache");
      cache2 = c2.getCache("cache");

      Thread.sleep(5000);

      System.out.println("c1 members: " + c1.getCachePeerListener("").getBoundCachePeers());
      System.out.println("c2 members" + c2.getCachePeerListener("").getBoundCachePeers());
      assert c1.getCachePeerListener("").getBoundCachePeers().size() == 1;
      assert c2.getCachePeerListener("").getBoundCachePeers().size() == 1;

      for (int i = 0; i < 100; i++)
      {
         cache1.put(new Element("key" + i, "value" + i));
         assert cache2.get("key" + i).getValue().equals("value" + i);
      }
      System.out.println(cache1.getKeys());
      System.out.println(cache2.getKeys());

      c1.shutdown();
      c2.shutdown();
   }


   public void testInvalidation() throws Exception 
   {
      Ehcache cache1;
      Ehcache cache2;

      CacheManager c1 = new CacheManager(cfgFile);
      CacheManager c2 = new CacheManager(cfgFile);

      cache1 = c1.getCache("cache");
      cache2 = c2.getCache("cache");

      Thread.sleep(5000);

      System.out.println("c1 members: " + c1.getCachePeerListener("").getBoundCachePeers());
      System.out.println("c2 members" + c2.getCachePeerListener("").getBoundCachePeers());
      assert c1.getCachePeerListener("").getBoundCachePeers().size() == 1;
      assert c2.getCachePeerListener("").getBoundCachePeers().size() == 1;

      cache1.put(new Element("key","value"));
      assert cache2.get("key").getValue().equals("value");
      cache2.put(new Element("key","newValue"));
      assert cache1.get("key") == null;

      c1.shutdown();
      c2.shutdown();
   }
}

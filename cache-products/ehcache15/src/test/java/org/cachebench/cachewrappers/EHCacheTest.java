package org.cachebench.cachewrappers;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import org.cachebench.CacheWrapper;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.Properties;

/**
 * // TODO: Add Javadocs
 *
 * @author <a href="mailto:manik@jboss.org">Manik Surtani</a>
 * @since 2.0.0
 */
@Test(enabled = false)
public class EHCacheTest
{

   public static void main(String[] args) throws Exception
   {


      CacheWrapper w = new EHCacheWrapper();
      Properties p = new Properties();
      p.setProperty("config", "ehcache-repl-sync.xml");
      w.init(p);

      Ehcache cache;
      URL url = new URL("file:///Users/manik/Code/CacheBenchFwk/cache-products/ehcache-1.2.4/conf/ehcache-repl-sync.xml");

      System.out.println("URL " + url);

      System.out.println("FIle: " + url.getFile());
      CacheManager m = CacheManager.create(url);

      System.out.println("Caches:");
      for (String s : m.getCacheNames()) System.out.println("   " + s);

      cache = m.getCache("cache");

      for (int i=0; i<100; i++) cache.put(new Element("key" + i, "value" + i));

      System.out.println(cache.getKeys());

      System.out.println("members: " + m.getCachePeerListener().getBoundCachePeers());

      m.shutdown();

   }
}

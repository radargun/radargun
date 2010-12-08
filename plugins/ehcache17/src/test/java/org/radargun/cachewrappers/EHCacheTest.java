package org.radargun.cachewrappers;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import org.radargun.CacheWrapper;
import org.testng.annotations.Test;

import java.net.URL;

/**
 * // TODO: Add Javadocs
 *
 * @author <a href="mailto:manik@jboss.org">Manik Surtani</a>
 * @since 2.0.0
 */
@Test(enabled = false)
public class EHCacheTest {

   public static void main(String[] args) throws Exception {
      CacheWrapper w = new EHCacheWrapper();
      w.setUp("ehcache-repl-sync.xml", false, 0);
      String cfgFile = "/path/to/file.xml";
      Ehcache cache;
      URL url = new URL(cfgFile);

      System.out.println("URL " + url);

      System.out.println("FIle: " + url.getFile());
      CacheManager m = CacheManager.create(url);

      System.out.println("Caches:");
      for (String s : m.getCacheNames()) System.out.println("   " + s);

      cache = m.getCache("cache");

      for (int i = 0; i < 100; i++) cache.put(new Element("key" + i, "value" + i));

      System.out.println(cache.getKeys());

      System.out.println("members: " + m.getCachePeerListener("RMI").getBoundCachePeers());

      m.shutdown();

   }
}

package org.radargun.cachewrappers;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;

/**
 * @author Mircea Markus
 */
@Test
public class LocalEhCacheTest {

   public void testLocalCacheConfig() {
      CacheManager cm = new CacheManager("/Users/mmarkus/github/radargun/plugins/ehcache25/src/test/resources/local.xml");
      Cache test = cm.getCache("test");
      test.put(new Element("k", "v"));
      assertEquals(test.get("k").getValue(), "v");
   }
}

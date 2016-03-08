import org.jboss.cache.Cache;
import org.jboss.cache.DefaultCacheFactory;
import org.testng.annotations.Test;

import static org.jboss.cache.CacheStatus.STARTED;
import static org.jboss.cache.config.Configuration.CacheMode.LOCAL;
import static org.jboss.cache.lock.IsolationLevel.READ_COMMITTED;
import static org.jboss.cache.lock.IsolationLevel.REPEATABLE_READ;

@Test(enabled = false)
public class TestConfigFiles {
   public void testConfigFilesRR() {
      Cache cache = new DefaultCacheFactory().createCache("mvcc/mvcc-local-RR.xml");
      cache.start();

      assert cache.getCacheStatus() == STARTED;

      assert cache.getConfiguration().getIsolationLevel() == REPEATABLE_READ;
      assert cache.getConfiguration().getCacheMode() == LOCAL;

      cache.stop();
   }

   public void testConfigFilesRC() {
      Cache cache = new DefaultCacheFactory().createCache("mvcc/mvcc-local-RC.xml");
      cache.start();

      assert cache.getCacheStatus() == STARTED;

      assert cache.getConfiguration().getIsolationLevel() == READ_COMMITTED;
      assert cache.getConfiguration().getCacheMode() == LOCAL;

      cache.stop();
   }
}

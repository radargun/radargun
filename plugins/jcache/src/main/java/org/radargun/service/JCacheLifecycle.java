package org.radargun.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Properties;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Lifecycle;
import org.radargun.utils.Utils;

/**
 * @author Matej Cimbora
 */
public class JCacheLifecycle implements Lifecycle {

   private static final Log log = LogFactory.getLog(JCacheLifecycle.class);
   private JCacheService service;

   public JCacheLifecycle(JCacheService service) {
      this.service = service;
   }

   @Override
   public void start() {
      URI cacheManagerUri = null;
      try {
         if (service.configFile != null) {
            URL cacheManagerUrl = JCacheLifecycle.class.getClassLoader().getResource(service.configFile);
            if (cacheManagerUrl == null) {
               throw new IllegalArgumentException(String.format("Unable to locate specified configuration file %s.", service.configFile));
            }
            cacheManagerUri = cacheManagerUrl.toURI();
         }
      } catch (URISyntaxException e) {
         log.errorf(String.format("Exception while accessing configuration file %s.", service.configFile), e);
         throw new IllegalStateException(e);
      }
      Properties configProperties = null;
      if (service.propertiesFile != null) {
         configProperties = new Properties();
         try (InputStream propertiesStream = JCacheLifecycle.class.getClassLoader().getResourceAsStream(service.propertiesFile)) {
            Utils.loadProperties(configProperties, propertiesStream);
         } catch (IOException e) {
            log.error(String.format("Exception while loading configuration properties file %s.", service.propertiesFile));
            throw new IllegalStateException(e);
         }
      }
      CachingProvider cachingProvider = service.cachingProviderClass == null ? Caching.getCachingProvider() : Caching.getCachingProvider(service.cachingProviderClass);
      service.cacheManager = cachingProvider.getCacheManager(cacheManagerUri, JCacheLifecycle.class.getClassLoader(), configProperties);
   }

   @Override
   public void stop() {
      service.cacheManager.destroyCache(service.cacheName);
      Caching.getCachingProvider().close();
   }

   @Override
   public boolean isRunning() {
      return service.cacheManager != null && !service.cacheManager.isClosed();
   }
}

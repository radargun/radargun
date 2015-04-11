package org.radargun.service;

import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.traits.ProvidesTrait;

import javax.cache.Cache;
import javax.cache.CacheManager;

/**
 * @author Matej Cimbora
 */
@Service(doc = "JSR-107 generic service")
public class JCacheService {

   @Property(name = Service.FILE, doc = "File used as a configuration for this service.")
   protected String configFile;

   @Property(doc = "Properties file providing additional way of configuring JSR-107 caches.")
   protected String propertiesFile;

   @Property(doc = "Default cache name.")
   protected String cacheName;

   @Property(doc = "Fully qualified class name of caching provider. Needs to be specified when multiple caching providers " +
         "are available on classpath.")
   protected String cachingProviderClass;

   // Initialized via {@link org.radargun.service.JCacheLifecycle} once the service is started.
   protected CacheManager cacheManager;

   @ProvidesTrait
   public JCacheOperations getJCacheOperations() {
      return new JCacheOperations(this);
   }

   @ProvidesTrait
   public JCacheLifecycle getJCacheLifecycle() {
      return new JCacheLifecycle(this);
   }

}

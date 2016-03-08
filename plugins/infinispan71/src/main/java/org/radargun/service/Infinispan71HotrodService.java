package org.radargun.service;

import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.configuration.NearCacheMode;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.config.PropertyDelegate;

/**
 * @author Vojtech Juranek &lt;vjuranek@redhat.com&gt;
 */
@Service(doc = Infinispan60HotrodService.SERVICE_DESCRIPTION)
public class Infinispan71HotrodService extends Infinispan70HotrodService {

   @PropertyDelegate(prefix = "nearCaching.")
   protected NearCaching nearCachingConfig = new NearCaching();

   protected ConfigurationBuilder getDefaultHotRodConfig() {
      ConfigurationBuilder cb = super.getDefaultHotRodConfig();
      cb.nearCache().mode(nearCachingConfig.mode).maxEntries(nearCachingConfig.maxEntries);
      return cb;
   }

   public static class NearCaching {
      @Property(doc = "Near caching mode. Default is DISABLED.")
      protected NearCacheMode mode = NearCacheMode.DISABLED;

      @Property(doc = "Maximum number or entires in near cache")
      protected int maxEntries = -1;
   }

}

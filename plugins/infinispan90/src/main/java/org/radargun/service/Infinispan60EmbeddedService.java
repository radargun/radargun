package org.radargun.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.infinispan.commons.util.FileLookupFactory;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.radargun.config.Property;
import org.radargun.traits.InternalsExposition;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.TimeService;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 */
public abstract class Infinispan60EmbeddedService extends Infinispan53EmbeddedService {

   @Property(doc = "Start thread periodically dumping JGroups state. Use for debug purposes. Default is false.")
   protected boolean jgroupsDumperEnabled = false;

   @Property(doc = "Applicable when jgroupsDumperEnabled == true, sets how often the statistics are recorded, the default is 10 seconds", converter = TimeConverter.class)
   protected long jgroupsDumperInterval = 10000;

   @Property(doc = "Enables presentation of internal state of Infinispan. Use for debugging and monitoring purposes. Default is false.")
   protected boolean internalsExpositionEnabled = false;

   protected JGroupsDumper jgroupsDumper;
   protected ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);

   public abstract InfinispanEmbeddedQueryable createQueryable();

   @ProvidesTrait
   public EmbeddedConfigurationProvider createConfigurationProvider() {
      return new EmbeddedConfigurationProvider60(this);
   }

   @ProvidesTrait
   public InternalsExposition createInternalsExposition() {
      return new Infinispan60InternalsExposition(this);
   }

   @Override
   protected ConfigurationBuilderHolder createConfiguration(String configFile) throws FileNotFoundException {
      ClassLoader classLoader = getClass().getClassLoader();
      try (InputStream input = FileLookupFactory.newInstance().lookupFileStrict(configFile, classLoader)) {
         return new ParserRegistry(classLoader).parse(input);
      } catch (IOException e) {
         log.error("Failed to get configuration input stream", e);
      }
      return null;
   }

   @Override
   protected String toString(InternalCacheEntry ice) {
      if (ice == null) return null;
      StringBuilder sb = new StringBuilder(256);
      sb.append(ice.getClass().getSimpleName());
      sb.append("[key=").append(ice.getKey()).append(", value=").append(ice.getValue());
      sb.append(", created=").append(ice.getCreated()).append(", isCreated=").append(ice.isCreated());
      sb.append(", lastUsed=").append(ice.getLastUsed()).append(", isChanged=").append(ice.isChanged());
      sb.append(", expires=").append(ice.getExpiryTime()).append(", isExpired=").append(ice.isExpired(TimeService.currentTimeMillis()));
      sb.append(", canExpire=").append(ice.canExpire()).append(", isEvicted=").append(ice.isEvicted());
      sb.append(", isRemoved=").append(ice.isRemoved()).append(", isValid=").append(ice.isValid());
      sb.append(", lifespan=").append(ice.getLifespan()).append(", maxIdle=").append(ice.getMaxIdle());
      return sb.append(']').toString();
   }

   @Override
   protected void beforeCacheManagerStart(final DefaultCacheManager cacheManager) {
      super.beforeCacheManagerStart(cacheManager);
      if (jgroupsDumperEnabled) {
         scheduledExecutor.schedule(new Runnable() {
            @Override
            public void run() {
               startJGroupsDumper(this);
            }
         }, 0, TimeUnit.MILLISECONDS);
      }
   }

   protected void startJGroupsDumper(Runnable thread) {
      JGroupsTransport transport = (JGroupsTransport) cacheManager.getTransport();
      if (transport == null || transport.getChannel() == null || !transport.getChannel().isOpen()) {
         // JGroups are not initialized, wait
         scheduledExecutor.schedule(thread, 1, TimeUnit.SECONDS);
      } else {
         jgroupsDumper = new JGroups4Dumper(transport.getChannel().getProtocolStack(), jgroupsDumperInterval);
         jgroupsDumper.start();
      }
   }

   @Override
   protected void stopCaches() {
      super.stopCaches();
      if (jgroupsDumper != null) jgroupsDumper.interrupt();
      jgroupsDumper = null;
   }

   @Override
   protected ConfigDumpHelper createConfigDumpHelper() {
      return new ConfigDumpHelper60();
   }
}

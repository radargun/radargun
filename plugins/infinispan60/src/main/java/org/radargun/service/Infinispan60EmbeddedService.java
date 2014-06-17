package org.radargun.service;

import java.io.FileNotFoundException;
import java.io.InputStream;

import org.infinispan.commons.util.FileLookupFactory;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan60EmbeddedService extends Infinispan53EmbeddedService {

   @Property(doc = "Start thread periodically dumping JGroups state. Use for debug purposes. Default is false.")
   private boolean jgroupsDumperEnabled = false;

   private JGroupsDumper jgroupsDumper;

   @ProvidesTrait
   public InfinispanEmbeddedQueryable createQueryable() {
      return new InfinispanEmbeddedQueryable(this);
   }

   @Override
   protected ConfigurationBuilderHolder createConfiguration(String configFile) throws FileNotFoundException {
      ClassLoader classLoader = getClass().getClassLoader();
      InputStream input = FileLookupFactory.newInstance().lookupFileStrict(configFile, classLoader);
      return new ParserRegistry(classLoader).parse(input);
   }

   @Override
   protected String toString(InternalCacheEntry ice) {
      if (ice == null) return null;
      StringBuilder sb = new StringBuilder(256);
      sb.append(ice.getClass().getSimpleName());
      sb.append("[key=").append(ice.getKey()).append(", value=").append(ice.getValue());
      sb.append(", created=").append(ice.getCreated()).append(", isCreated=").append(ice.isCreated());
      sb.append(", lastUsed=").append(ice.getLastUsed()).append(", isChanged=").append(ice.isChanged());
      sb.append(", expires=").append(ice.getExpiryTime()).append(", isExpired=").append(ice.isExpired(System.currentTimeMillis()));
      sb.append(", canExpire=").append(ice.canExpire()).append(", isEvicted=").append(ice.isEvicted());
      sb.append(", isRemoved=").append(ice.isRemoved()).append(", isValid=").append(ice.isValid());
      sb.append(", lifespan=").append(ice.getLifespan()).append(", maxIdle=").append(ice.getMaxIdle());
      return sb.append(']').toString();
   }

   @Override
   protected void startCaches() throws Exception {
      super.startCaches();
      if (jgroupsDumperEnabled) {
         jgroupsDumper = new JGroupsDumper(((JGroupsTransport) ((DefaultCacheManager) cacheManager).getTransport())
               .getChannel().getProtocolStack());
         jgroupsDumper.start();
      }
   }

   @Override
   protected void stopCaches() {
      super.stopCaches();
      if (jgroupsDumper != null) jgroupsDumper.interrupt();
      jgroupsDumper = null;
   }

   protected ConfigDumpHelper createConfigDumpHelper() {
      return new ConfigDumpHelper60();
   }   
}

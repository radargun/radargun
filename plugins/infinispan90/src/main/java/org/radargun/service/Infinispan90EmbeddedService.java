package org.radargun.service;

import java.util.concurrent.TimeUnit;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.jgroups.protocols.TP;
import org.radargun.Service;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Roman Macor (rmacor@redhat.com)
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan90EmbeddedService extends Infinispan82EmbeddedService {

   @Override
   protected Infinispan90Lifecycle createLifecycle() {
      return new Infinispan90Lifecycle(this);
   }

   @Override
   @ProvidesTrait
   public InfinispanCacheInfo createCacheInformation() {
      return new Infinispan90CacheInfo(this);
   }

   @ProvidesTrait
   public Infinispan90AsyncOperations createAsyncOperations() {
      return new Infinispan90AsyncOperations(this);
   }

   @Override
   protected TP getTransportProtocol() {
      JGroupsTransport transport = (JGroupsTransport) cacheManager.getTransport();
      return (TP) transport.getChannel().getProtocolStack().findProtocol(TP.class);
   }

   @Override
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
}
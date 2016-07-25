package org.radargun.service;

import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.jgroups.protocols.TP;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan70EmbeddedService extends Infinispan60EmbeddedService {

   @Property(doc = "Enable diagnostics port for service probing (true/false/null). Default is null - use settings from ISPN configuration.")
   protected Boolean enableDiagnostics;

   @Override
   protected void startCaches() throws Exception {
      super.startCaches();
      setDiagnostics();
   }

   protected void setDiagnostics() {
      JGroupsTransport transport = (JGroupsTransport) cacheManager.getTransport();
      TP transportprotocol = (TP) transport.getChannel().getProtocolStack().findProtocol(TP.class);
      if (Boolean.TRUE.equals(enableDiagnostics)) {
         transportprotocol.enableDiagnostics();
         log.debug("Enabling diagnostics in the transport protocol");
      } else if (Boolean.FALSE.equals(enableDiagnostics)) {
         transportprotocol.disableDiagnostics();
         log.debug("Disabling diagnostics in the transport protocol");
      }
   }

   @Override
   @ProvidesTrait
   public InfinispanCacheInfo createCacheInformation() {
      return new Infinispan70CacheInfo(this);
   }

   @Override
   @ProvidesTrait
   public InfinispanEmbeddedQueryable createQueryable() {
      return new Infinispan70EmbeddedQueryable(this);
   }

   @ProvidesTrait
   public InfinispanIterable createIterable() {
      return new InfinispanIterable(this);
   }

   @ProvidesTrait
   public InfinispanCacheListeners createListeners() {
      return new InfinispanCacheListeners(this);
   }

   @ProvidesTrait
   public Infinispan70TopologyHistory getInfinispan70TopologyHistory() {
      return (Infinispan70TopologyHistory) topologyAware;
   }

   @Override
   protected InfinispanTopologyHistory createTopologyAware() {
      return new Infinispan70TopologyHistory(this);
   }
}

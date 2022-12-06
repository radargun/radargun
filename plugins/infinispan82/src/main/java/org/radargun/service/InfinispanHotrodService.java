package org.radargun.service;

import java.lang.reflect.Field;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.impl.transport.tcp.TcpTransport;
import org.infinispan.client.hotrod.impl.transport.tcp.TcpTransportFactory;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.InternalsExposition;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Service(doc = InfinispanHotrodService.SERVICE_DESCRIPTION)
public class InfinispanHotrodService implements Lifecycle, InternalsExposition {
   protected static final Log log = LogFactory.getLog(InfinispanHotrodService.class);
   protected static final String SERVICE_DESCRIPTION = "HotRod client";

   @Property(name = "cache", doc = "Default cache name. By default, it's the default cache as retrived with getCache().")
   protected String cacheName;

   @Property(doc = "List of server addresses the clients should connect to, separated by semicolons (;).")
   protected String servers;

   // due to a bug in RCM, we have to duplicate the managers
   protected RemoteCacheManager managerNoReturn;
   protected RemoteCacheManager managerForceReturn;

   private volatile Field transportFactoryField = null;

   @ProvidesTrait
   public HotRodOperations createOperations() {
      return new HotRodOperations(this);
   }

   @ProvidesTrait
   public Lifecycle createLifecycle() {
      return this;
   }

   @ProvidesTrait
   public InternalsExposition createExposition() {
      return this;
   }

   @Override
   public void start() {
      managerNoReturn = new RemoteCacheManager(servers, true);
      managerForceReturn = new RemoteCacheManager(servers, true);
   }

   @Override
   public void stop() {
      managerNoReturn.stop();
      managerNoReturn = null;
      managerForceReturn.stop();
      managerForceReturn = null;
   }

   @Override
   public boolean isRunning() {
      return managerNoReturn != null && managerNoReturn.isStarted();
   }

   /*
    * ISPN10 removed TcpTransportFactory and a class not found exception will throws.
    */
   protected Object getTransportFactory(RemoteCacheManager manager) {
      try {
         if (transportFactoryField == null) {
            Field tf = manager.getClass().getDeclaredField("transportFactory");
            tf.setAccessible(true);
            transportFactoryField = tf;
         }
         Object factory = transportFactoryField.get(manager);
         if (factory == null) {
            log.debug("Transport factory is null");
            return null;
         } else if (factory instanceof TcpTransportFactory) {
            return (TcpTransportFactory) factory;
         } else {
            log.errorf("Transport factory is '%s' (%s)", factory, factory.getClass().getName());
            return null;
         }
      } catch (Exception e) {
         log.error("Failed to retrieve transport factory", e);
         return null;
      }
   }

   @Override
   public Map<String, Number> getValues() {
      Map<String, Number> values = new HashMap<>();
      TcpTransportFactory nrFactory = (TcpTransportFactory) getTransportFactory(managerNoReturn);
      TcpTransportFactory frFactory = (TcpTransportFactory) getTransportFactory(managerForceReturn);
      if (nrFactory != null) {
         GenericKeyedObjectPool<SocketAddress, TcpTransport> nrConnectionPool = nrFactory.getConnectionPool();
         values.put("NR ConnectionPool Active", nrConnectionPool.getNumActive());
         values.put("NR ConnectionPool Idle", nrConnectionPool.getNumIdle());
      }
      if (frFactory != null) {
         GenericKeyedObjectPool<SocketAddress, TcpTransport> frConnectionPool = frFactory.getConnectionPool();
         values.put("FR ConnectionPool Active", frConnectionPool.getNumActive());
         values.put("FR ConnectionPool Idle", frConnectionPool.getNumIdle());
      }
      return values;
   }

   @Override
   public String getCustomStatistics(String type) {
      return null;
   }

   @Override
   public void resetCustomStatistics(String type) {
   }

   public String getCacheName() {
      return cacheName;
   }

   public RemoteCacheManager getManagerNoReturn() {
      return managerNoReturn;
   }

   public RemoteCacheManager getManagerForceReturn() {
      return managerForceReturn;
   }
}

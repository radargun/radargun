package org.radargun.service;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import org.infinispan.executors.LazyInitializingBlockingTaskAwareExecutorService;
import org.infinispan.executors.LazyInitializingExecutorService;
import org.infinispan.factories.GlobalComponentRegistry;
import org.infinispan.factories.KnownComponentNames;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.infinispan.util.concurrent.BlockingTaskAwareExecutorServiceImpl;
import org.jgroups.protocols.TP;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.InternalsExposition;

/**
 * Exposes thread states
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Infinispan60InternalsExposition implements InternalsExposition {
   protected static final Log log = LogFactory.getLog(Infinispan60InternalsExposition.class);
   protected final Infinispan60EmbeddedService service;

   private final Field lazyInitializingExecutorServiceDelegate;
   private final Field blockingTaskAwareExecutorServiceDelegate;
   private final Field lazyInitializingBlockingTaskAwareExecutorServiceDelegate;

   public Infinispan60InternalsExposition(Infinispan60EmbeddedService service) {
      this.service = service;
      this.lazyInitializingExecutorServiceDelegate = getLazyInitializingExecutorServiceDelegate();
      this.blockingTaskAwareExecutorServiceDelegate = getBlockingTaskAwareExecutorServiceDelegate();
      this.lazyInitializingBlockingTaskAwareExecutorServiceDelegate = getLazyInitializingBlockingTaskAwareExecutorServiceDelegate();
   }

   @Override
   public Map<String, Number> getValues() {
      if (!service.internalsExpositionEnabled) {
         return Collections.EMPTY_MAP;
      }
      Map<String, Number> values = new HashMap<>();
      GlobalComponentRegistry globalComponentRegistry = service.cacheManager.getGlobalComponentRegistry();
      addValues(findTPE(globalComponentRegistry.getComponent(KnownComponentNames.ASYNC_TRANSPORT_EXECUTOR)), "Async Transport Executor", values);
      addValues(findTPE(globalComponentRegistry.getComponent(KnownComponentNames.REMOTE_COMMAND_EXECUTOR)), "Remote Commands Executor", values);
      JGroupsTransport transport = (JGroupsTransport) service.cacheManager.getTransport();
      if (transport != null) {
         TP tp = (TP) transport.getChannel().getProtocolStack().getBottomProtocol();
         addValues((ThreadPoolExecutor) tp.getOOBThreadPool(), "OOB", values);
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

   private void addValues(ThreadPoolExecutor threadPoolExecutor, final String executorName, Map<String, Number> values) {
      if (threadPoolExecutor == null) return;
      values.put(executorName + " Active", threadPoolExecutor.getActiveCount());
      values.put(executorName + " Total", threadPoolExecutor.getPoolSize());
   }

   protected Field getLazyInitializingExecutorServiceDelegate() {
      return getExecutorServiceDelegate(LazyInitializingExecutorService.class, "delegate");
   }

   protected Field getBlockingTaskAwareExecutorServiceDelegate() {
      return getExecutorServiceDelegate(BlockingTaskAwareExecutorServiceImpl.class, "executorService");
   }

   protected Field getLazyInitializingBlockingTaskAwareExecutorServiceDelegate() {
      return getExecutorServiceDelegate(LazyInitializingBlockingTaskAwareExecutorService.class, "delegate");
   }

   protected Field getExecutorServiceDelegate(Class<?> clazz, String fieldName) {
      Field field;
      try {
         field = clazz.getDeclaredField(fieldName);
         field.setAccessible(true);
      } catch (NoSuchFieldException e) {
         log.error("Failed to load field: " + fieldName, e);
         throw new RuntimeException(e);
      }
      return field;
   }

   private ThreadPoolExecutor findTPE(Object executorService) {
      try {
         if (executorService == null) {
            return null;
         } else if (executorService instanceof ThreadPoolExecutor) {
            return (ThreadPoolExecutor) executorService;
         } else if (executorService instanceof LazyInitializingExecutorService) {
            return findTPE(lazyInitializingExecutorServiceDelegate.get(executorService));
         } else if (executorService instanceof BlockingTaskAwareExecutorServiceImpl) {
            return findTPE(blockingTaskAwareExecutorServiceDelegate.get(executorService));
         } else if (executorService instanceof LazyInitializingBlockingTaskAwareExecutorService) {
            return findTPE(lazyInitializingBlockingTaskAwareExecutorServiceDelegate.get(executorService));
         } else {
            log.debug("Failed to retrieve thread pool executor from " + executorService);
            return null;
         }
      } catch (IllegalAccessException e) {
         log.error("Failed to retrieve thread poool executor from " + executorService);
         return null;
      }
   }
}

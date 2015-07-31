package org.radargun.service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.infinispan.Cache;
import org.infinispan.distexec.DefaultExecutorService;
import org.infinispan.distexec.DistributedExecutorService;
import org.infinispan.distexec.DistributedTask;
import org.infinispan.distexec.DistributedTaskBuilder;
import org.infinispan.distexec.DistributedTaskExecutionPolicy;
import org.infinispan.distexec.DistributedTaskFailoverPolicy;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.remoting.transport.Address;
import org.infinispan.remoting.transport.Transport;
import org.radargun.config.Destroy;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.DistributedTaskExecutor;
import org.radargun.utils.Utils;

/**
 * A CacheWrapper that implements the DistributedTaskCapable interface, so it is capable of
 * executing a Callable against the cache using the DistributedExecutorService.
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */

public class InfinispanDistributedTask<K, V, T> implements DistributedTaskExecutor<T> {

   protected final Log log = LogFactory.getLog(getClass());
   protected final Infinispan52EmbeddedService service;
   protected final ExecutorService executorService = Executors.newCachedThreadPool(new ThreadFactory() {
      AtomicInteger counter = new AtomicInteger();
      @Override
      public Thread newThread(Runnable r) {
         return new Thread(r, "DistributedTask-" + counter.incrementAndGet());
      }
   });

   public InfinispanDistributedTask(Infinispan52EmbeddedService service) {
      this.service = service;
   }

   @Destroy
   public void destroy() {
      Utils.shutdownAndWait(executorService);
   }

   protected class Builder implements DistributedTaskExecutor.Builder<T> {
      protected final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      protected final DistributedExecutorService executorService;
      protected Callable<T> callable;
      protected DistributedTaskExecutionPolicy executionPolicy;
      protected DistributedTaskFailoverPolicy failoverPolicy;
      protected Address target;

      public Builder(Cache<K, V> cache) {
         executorService = new DefaultExecutorService(cache, InfinispanDistributedTask.this.executorService);
      }

      @Override
      public DistributedTaskExecutor.Builder callable(Callable<T> callable) {
         this.callable = callable;
         return this;
      }

      @Override
      public DistributedTaskExecutor.Builder executionPolicy(String executionPolicy) {
         this.executionPolicy = DistributedTaskExecutionPolicy.valueOf(executionPolicy);
         return this;
      }

      @Override
      public DistributedTaskExecutor.Builder failoverPolicy(String failoverPolicy) {
         this.failoverPolicy = Utils.instantiate(failoverPolicy);
         return this;
      }

      @Override
      public DistributedTaskExecutor.Builder nodeAddress(String nodeAddress) {
         this.target = findHostPhysicalAddress(nodeAddress);
         return this;
      }

      @Override
      public Task build() {
         DistributedTaskBuilder<T> taskBuilder = executorService.createDistributedTaskBuilder(callable);
         if (executionPolicy != null) taskBuilder.executionPolicy(executionPolicy);
         if (failoverPolicy != null) taskBuilder.failoverPolicy(failoverPolicy);
         return new Task(executorService, taskBuilder.build(), target);
      }
   }

   protected class Task implements DistributedTaskExecutor.Task<T> {
      protected final DistributedExecutorService executorService;
      protected final DistributedTask<T> task;
      protected final Address target;

      public Task(DistributedExecutorService executorService, DistributedTask<T> task, Address target) {
         this.executorService = executorService;
         this.task = task;
         this.target = target;
      }

      @Override
      public List<Future<T>> execute() {
         if (target != null) {
            return Collections.singletonList((Future<T>) executorService.submit(target, task));
         } else {
            return executorService.submitEverywhere(task);
         }
      }
   }

   @Override
   public Builder builder(String cacheName) {
      return new Builder((Cache<K, V>) service.getCache(cacheName));
   }

   private Address findHostPhysicalAddress(String nodeAddress) {
      Transport t = ((DefaultCacheManager) service.cacheManager).getTransport();
      if (t != null) {
         for (Address address : t.getPhysicalAddresses()) {
            if (address.toString().contains(nodeAddress)) {
               return address;
            }
         }
      }
      throw new IllegalArgumentException("Cannot find node with address " + nodeAddress);
   }

}

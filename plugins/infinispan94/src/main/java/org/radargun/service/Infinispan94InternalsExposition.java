package org.radargun.service;

import java.lang.reflect.Field;

import org.infinispan.executors.LazyInitializingBlockingTaskAwareExecutorService;
import org.infinispan.executors.LazyInitializingExecutorService;

/**
 * Exposes thread states
 *
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
public class Infinispan94InternalsExposition extends Infinispan60InternalsExposition {

   public Infinispan94InternalsExposition(Infinispan94EmbeddedService service) {
      super(service);
   }

   protected Field getLazyInitializingExecutorServiceDelegate() {
      return getExecutorServiceDelegate(LazyInitializingExecutorService.class.getSuperclass(), "executor");
   }

   protected Field getLazyInitializingBlockingTaskAwareExecutorServiceDelegate() {
      return getExecutorServiceDelegate(LazyInitializingBlockingTaskAwareExecutorService.class.getSuperclass(), "executor");
   }
}

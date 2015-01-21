package org.radargun.traits;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

@Trait(doc = "Provides the interface for executing distributed tasks.")
public interface DistributedTaskExecutor<T> {

   interface Builder<T> {
      /**
       * @param callable Distributed task that should be executed.
       * @return This builder instance.
       */
      Builder callable(Callable<T> callable);

      /**
       * @param executionPolicy Custom argument for task execution.
       * @return This builder instance.
       */
      Builder executionPolicy(String executionPolicy);

      /**
       * @param failoverPolicy Custom argument for failover.
       * @return This builder instance.
       */
      Builder failoverPolicy(String failoverPolicy);

      /**
       * // TODO we should rather set list of slave ids where this should be executed
       * @param nodeAddress Describes target node in plugin-specific way.
       * @return
       */
      Builder nodeAddress(String nodeAddress);

      /**
       * @return Task to be executed
       */
      Task build();
   }

   interface Task<T> {
      /**
       * Start task execution and return futures for completion of the task.
       */
      List<Future<T>> execute();
   }

   Builder builder(String cacheName);
}

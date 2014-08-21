package org.radargun.traits;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

@Trait(doc = "Provides the interface for executing distributed tasks.")
public interface DistributedTaskExecutor<T> {

   /**
    * 
    * This method executes the specified Distributed task against all of the keys in the cache.
    * 
    * @param distributedCallableFqn
    *           The fully qualified class name for the org.infinispan.distexec.DistributedCallable
    *           or java.util.concurrent.Callable implementation. The implementation must have a no
    *           argument constructor. This class name is required.
    *
    * @param executionPolicyName
    *           The name of one of the org.infinispan.distexec.DistributedTaskExecutionPolicy enums
    *           or <code>null</code>
    *
    * @param failoverPolicyFqn
    *           The fully qualified class name for a
    *           org.infinispan.distexec.DistributedTaskFailoverPolicy implementation or
    *           <code>null</code>
    *
    * @param nodeAddress
    *
    * @param params
    *
    * @return
    */
   public List<Future<T>> executeDistributedTask(String distributedCallableFqn,
                                                 String executionPolicyName, String failoverPolicyFqn, String nodeAddress, Map<String, String> params);
}

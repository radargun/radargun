/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.radargun.stages;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.features.DistributedTaskCapable;
import org.radargun.state.MasterState;
import org.radargun.utils.Utils;

/**
 * Executes a Callable or DistributedCallable against the cache using the
 * DistributedExecutorService. The execution and failover policies can be specified. If the IP
 * address of a node is specified in <code>nodeAddress</code>, then the Callable is only executed on
 * that node. Public String Fields on the Callable object can be set using the
 * <code>distributedExecutionParams</code> property.
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Stage(doc = "Stage which executes a MapReduce Task against all keys in the cache.")
public class DistributedTaskStage<K, V, T> extends AbstractDistStage {

   private final String FIRST_SCALE_STAGE_KEY = "firstScalingStage";

   @Property(optional = false, doc = "Fully qualified class name of the "
         + "org.infinispan.distexec.DistributedCallable or "
         + "java.util.concurrent.Callable implementation to execute.")
   private String distributedCallableFqn;

   @Property(optional = true, doc = "The name of one of the "
         + "org.infinispan.distexec.DistributedTaskExecutionPolicy enums. " 
         + "The default is null.")
   private String executionPolicyName;

   @Property(optional = true, doc = "The fully qualified class name for a custom "
         + "org.infinispan.distexec.DistributedTaskFailoverPolicy implementation. " 
         + "The default is null.")
   private String failoverPolicyFqn = null;

   @Property(optional = true, doc = "The node address where the task will be "
         + "executed. The default is null, and tasks will be executed against " 
         + "all nodes in the cluster.")
   private String nodeAddress = null;
   
   @Property(optional = true, doc = "A String in the form of "
         + "'methodName:methodParameter;methodName1:methodParameter1' that allows"
         + " invoking a method on the distributedCallableFqn Object. The method"
         + " must be public and take a String parameter.")
   private String distributedExecutionParams = null;

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      super.processAckOnMaster(acks, masterState);
      StringBuilder reportCsvContent = new StringBuilder();

      if (masterState.get(FIRST_SCALE_STAGE_KEY) == null) {
         masterState.put(FIRST_SCALE_STAGE_KEY, masterState.nameOfTheCurrentBenchmark());
         reportCsvContent.append("NODE_INDEX, NUMBER_OF_NODES, KEY_COUNT_ON_NODE, DURATION_MSEC\n");
      }

      for (DistStageAck ack : acks) {
         DefaultDistStageAck dack = (DefaultDistStageAck) ack;
         reportCsvContent.append((String) dack.getPayload()).append("\n");
      }
      reportCsvContent.append("\n");

      try {
         Utils.createOutputFile("distributedexecution.csv", reportCsvContent.toString(), false);
      } catch (IOException e) {
         log.error("Failed to create report.", e);
      }

      return true;
   }

   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck result = newDefaultStageAck();
      CacheWrapper cacheWrapper = slaveState.getCacheWrapper();
      List<Future<T>> futureList = null;
      List<T> resultList = new ArrayList<T>();

      if (cacheWrapper == null) {
         result.setErrorMessage("Not running test on this slave as the wrapper hasn't been configured.");
      } else {
         if (getSlaveIndex() == 0) {

            if (cacheWrapper instanceof DistributedTaskCapable) {
               if (distributedCallableFqn != null) {
                  log.info("--------------------");
                  @SuppressWarnings("unchecked")
                  DistributedTaskCapable<K, V, T> distributedTaskCapable = (DistributedTaskCapable<K, V, T>) cacheWrapper;
                  long durationMillis;
                  long start = System.currentTimeMillis();
                  futureList = distributedTaskCapable.executeDistributedTask(classLoadHelper, distributedCallableFqn,
                        executionPolicyName, failoverPolicyFqn, nodeAddress,
                        Utils.parseParams(distributedExecutionParams));
                  if (futureList != null) {
                     for (Future<T> future : futureList) {
                        try {
                           resultList.add(future.get());
                        } catch (InterruptedException e) {
                           result.setError(true);
                           result.setErrorMessage("The distributed task was interrupted.");
                           result.setRemoteException(e);
                        } catch (ExecutionException e) {
                           result.setError(true);
                           result.setErrorMessage("An error occurred executing the distributed task.");
                           result.setRemoteException(e);
                        }
                     }
                  } else {
                     result.setError(true);
                     result.setErrorMessage("No future objects returned from executing the distributed task.");
                  }
                  durationMillis = System.currentTimeMillis() - start;

                  log.info("Distributed Execution task completed in " + Utils.prettyPrintMillis(durationMillis));
                  String payload = this.slaveIndex + ", " + cacheWrapper.getNumMembers() + ", "
                        + cacheWrapper.getLocalSize() + ", " + durationMillis;
                  result.setPayload(payload);
                  log.info(cacheWrapper.getNumMembers() + " nodes were used. " + cacheWrapper.getLocalSize()
                        + " entries on this node");
                  log.info(cacheWrapper.getInfo());
                  log.info("Distributed execution results:");
                  log.info("--------------------");
                  for (T t : resultList) {
                     log.info(t.toString());
                  }
                  log.info("--------------------");
               } else {
                  result.setError(true);
                  result.setErrorMessage("The distributed task or callable class must be specified.");
               }
            } else {
               result.setError(true);
               result.setErrorMessage("Distributed Execution is not supported by this cache");
            }
         } else {
            String payload = this.slaveIndex + ", " + cacheWrapper.getNumMembers() + ", " + cacheWrapper.getLocalSize()
                  + ", 0";
            result.setPayload(payload);
         }

      }
      return result;
   }

   public String getDistributedCallableFqn() {
      return distributedCallableFqn;
   }

   public void setDistributedCallableFqn(String distributedCallableFqn) {
      this.distributedCallableFqn = distributedCallableFqn;
   }

   public String getExecutionPolicyName() {
      return executionPolicyName;
   }

   public void setExecutionPolicyName(String executionPolicyName) {
      this.executionPolicyName = executionPolicyName;
   }

   public String getFailoverPolicyFqn() {
      return failoverPolicyFqn;
   }

   public void setFailoverPolicyFqn(String failoverPolicyFqn) {
      this.failoverPolicyFqn = failoverPolicyFqn;
   }

   public String getNodeAddress() {
      return nodeAddress;
   }

   public void setNodeAddress(String nodeAddress) {
      this.nodeAddress = nodeAddress;
   }

   public String getDistributedExecutionParams() {
      return distributedExecutionParams;
   }

   public void setDistributedExecutionParams(String distributedExecutionParams) {
      this.distributedExecutionParams = distributedExecutionParams;
   }

}

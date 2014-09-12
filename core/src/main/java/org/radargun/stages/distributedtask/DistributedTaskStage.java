package org.radargun.stages.distributedtask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.SlaveState;
import org.radargun.traits.CacheInformation;
import org.radargun.traits.Clustered;
import org.radargun.traits.DistributedTaskExecutor;
import org.radargun.traits.InjectTrait;
import org.radargun.utils.Projections;
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
         + "org.infinispan.distexec.DistributedTaskExecutionPolicy enums. The default is null.")
   private String executionPolicyName;

   @Property(optional = true, doc = "The fully qualified class name for a custom "
         + "org.infinispan.distexec.DistributedTaskFailoverPolicy implementation. The default is null.")
   private String failoverPolicyFqn = null;

   @Property(optional = true, doc = "The node address where the task will be "
         + "executed. The default is null, and tasks will be executed against all nodes in the cluster.")
   private String nodeAddress = null;

   @Property(optional = true, doc = "A String in the form of "
         + "'methodName:methodParameter;methodName1:methodParameter1' that allows"
         + " invoking a method on the distributedCallableFqn Object. The method"
         + " must be public and take a String parameter.")
   private String distributedExecutionParams = null;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private DistributedTaskExecutor executor;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private CacheInformation cacheInformation;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private Clustered clustered;

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks) {
      super.processAckOnMaster(acks);
      StringBuilder reportCsvContent = new StringBuilder();

      // TODO: move this into test report
      if (masterState.get(FIRST_SCALE_STAGE_KEY) == null) {
         masterState.put(FIRST_SCALE_STAGE_KEY, masterState.getConfigName());
         reportCsvContent.append("NODE_INDEX, NUMBER_OF_NODES, KEY_COUNT_ON_NODE, DURATION_NANOSECONDS\n");
      }

      for (TextAck ack : Projections.instancesOf(acks, TextAck.class)) {
         reportCsvContent.append(ack.getText()).append("\n");
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
      if (!isServiceRunnning()) {
         return errorResponse("Service not running", null);
      }

      if (distributedCallableFqn == null) {
         return errorResponse("The distributed task or callable class must be specified.", null);
      }

      if (slaveState.getSlaveIndex() == 0) {
         return executeTask();
      } else {
         return new TextAck(slaveState);
      }
   }

   private String getPayload(long durationNanos) {
      return slaveState.getSlaveIndex() + ", " + clustered.getClusteredNodes() + ", " + cacheInformation.getCache(null).getLocalSize() + ", " + durationNanos;
   }

   private DistStageAck executeTask() {
      List<Future<T>> futureList = null;
      List<T> resultList = new ArrayList<T>();

      log.info("--------------------");
      long start = System.nanoTime();
      futureList = executor.executeDistributedTask(distributedCallableFqn,
            executionPolicyName, failoverPolicyFqn, nodeAddress, Utils.parseParams(distributedExecutionParams));
      TextAck ack = new TextAck(slaveState);
      if (futureList == null) {
         ack.error("No future objects returned from executing the distributed task.");
      } else {
         for (Future<T> future : futureList) {
            try {
               resultList.add(future.get());
            } catch (InterruptedException e) {
               ack.error("The distributed task was interrupted.", e);
            } catch (ExecutionException e) {
               ack.error("An error occurred executing the distributed task.", e);
            }
         }
      }
      long durationNanos = System.nanoTime() - start;
      ack.setText(getPayload(durationNanos));

      log.info("Distributed Execution task completed in " + Utils.prettyPrintTime(durationNanos, TimeUnit.NANOSECONDS));
      log.info(clustered.getClusteredNodes() + " nodes were used. " + cacheInformation.getCache(null).getLocalSize() + " entries on this node");
      log.info("Distributed execution results:");
      log.info("--------------------");
      for (T t : resultList) {
         log.info(t.toString());
      }
      log.info("--------------------");
      return ack;
   }

   private static class TextAck extends DistStageAck {
      private String text;

      private TextAck(SlaveState slaveState) {
         super(slaveState);
         this.text = text;
      }

      public void setText(String text) {
         this.text = text;
      }

      public String getText() {
         return text;
      }
   }
}

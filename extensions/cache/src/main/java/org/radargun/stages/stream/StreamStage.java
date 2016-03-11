package org.radargun.stages.stream;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.cache.RandomDataStage;
import org.radargun.state.SlaveState;
import org.radargun.stats.DataOperationStats;
import org.radargun.stats.DefaultStatistics;
import org.radargun.stats.Request;
import org.radargun.stats.Statistics;
import org.radargun.traits.CacheInformation;
import org.radargun.traits.Clustered;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Streamable;
import org.radargun.utils.Utils;

/**
 * @author Roman Macor &lt;rmacor@redhat.com&gt;
 */
@Stage(doc = "Stage which executes a specified stream Task against all keys in the cache.")
public class StreamStage extends AbstractDistStage {

   @Property(doc = "Name of the test as used for reporting. Default is 'Stream_Stage'.")
   protected String testName = "Stream_Stage";

   @Property(doc = "Boolean value that determines if the "
      + "final results of the stream are written to the log of the "
      + "first slave node. The default is false.")
   private boolean printResult = false;

   @Property(doc = "The number of times to execute the stream task. The default is 10.")
   private int numExecutions = 10;

   @Property(doc = "Name of the cache where stream task should be executed. Default is the default cache.")
   private String cacheName;

   @Property(doc = "The name of the key in the MasterState object that returns the total number of "
      + "bytes processed by the stream task. The default is RandomDataStage.RANDOMDATA_TOTALBYTES_KEY.")
   private String totalBytesKey = RandomDataStage.RANDOMDATA_TOTALBYTES_KEY;

   @Property(optional = false, doc = "Fully qualified class name of the StreamConsumer implementation.")
   private String streamOperationClass;

   @Property(doc = "Boolean value that determines if the parallelStream is used ")
   private boolean parallelStream = false;

   @Property(name = "statistics", doc = "Type of gathered statistics. Default are the 'dataOperation' statistics " +
      "(fixed size memory footprint for each operation).", complexConverter = Statistics.Converter.class)
   protected Statistics statisticsPrototype = new DefaultStatistics(new DataOperationStats());

   @InjectTrait
   private Streamable streamable;

   @InjectTrait()
   private Clustered clustered;

   @InjectTrait()
   private CacheInformation cacheInformation;

   private StreamFunction function;

   private Object streamTaskResult;

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMaster(acks);
      if (result.isError())
         return result;

      Report report = masterState.getReport();
      Report.Test test = report.createTest(testName, null, true);
      int testIteration = test.getIterations().size();

      Map<Integer, Report.SlaveResult> numberOfResultKeysResult = new HashMap<>();
      Map<Integer, Report.SlaveResult> durationsResult = new HashMap<>();

      for (StreamStageAck ack : instancesOf(acks, StreamStageAck.class)) {
         if (ack.stats != null) {
            DataOperationStats opStats = (DataOperationStats) ack.stats.getOperationsStats().get(Streamable.STREAMABLE.name);

            opStats.setTotalBytes((Long) masterState.get(totalBytesKey));
            test.addStatistics(testIteration, ack.getSlaveIndex(), Collections.singletonList(ack.stats));
            durationsResult.put(ack.getSlaveIndex(), new Report.SlaveResult(opStats.getResponseTimes(), false));
            test.addResult(testIteration, new Report.TestResult("Stream task durations on slave" + ack.getSlaveIndex(), durationsResult, "", false));
         }
         if (ack.numberOfResultKeys != null) {
            numberOfResultKeysResult.put(ack.getSlaveIndex(), new Report.SlaveResult(ack.numberOfResultKeys, false));
            test.addResult(testIteration, new Report.TestResult("Key count in stream result map on slave" + ack.getSlaveIndex(),
               numberOfResultKeysResult, "", false));
         }
      }
      return StageResult.SUCCESS;
   }

   @Override
   public DistStageAck executeOnSlave() {
      if (!isServiceRunning()) {
         return errorResponse("Service is not runnning", null);
      }

      DistStageAck result = null;
      Object prevResult = null;

      Statistics stats = statisticsPrototype.copy();
      stats.begin();

      for (int i = 0; i < numExecutions; i++) {
         prevResult = streamTaskResult;

         result = executeStreamTask(stats);

         if (prevResult != null) {
            if (prevResult.equals(streamTaskResult)) {
               log.info(i + ": Got the same results for two Stream tasks runs");
            } else {
               log.error(i + ": Did not get the same results for two Stream tasks runs");
               break;
            }
         }
         if (result.isError() && exitOnFailure) {
            break;
         }
      }
      stats.end();

      log.info("Executed stream stage on slave");
      return result;
   }

   private void configureStreamTask() {
      try {
         function = Utils.instantiate(streamOperationClass);
      } catch (Exception e) {
         throw new IllegalArgumentException("Could not instantiate StreamConsumer implementation class: " + streamOperationClass, e);
      }
   }

   private DistStageAck executeStreamTask(Statistics stats) {
      long durationNanos;
      long start;

      configureStreamTask();

      StreamStageAck ack = new StreamStageAck(slaveState);
      Request request = stats.startRequest();
      try {

         if (parallelStream) {
            streamTaskResult = function.apply(streamable.parallelStream(cacheName));
         } else {
            streamTaskResult = function.apply(streamable.stream(cacheName));
         }

         request.succeeded(Streamable.STREAMABLE);
         log.info("Stream task completed in "
            + Utils.prettyPrintTime(request.duration(), TimeUnit.NANOSECONDS));

         long resultCount = function.getResultCount();

         log.info("Result map contains '" + resultCount + "' keys.");
         ack.setNumberOfResultKeys(resultCount);

         ack.setStats(stats);

         if (printResult) {
            String printableResult = function.getPrintableResult();
            if (printableResult != null) {
               log.info("Stream task result:" + printableResult);
            }
         }
      } catch (Exception e) {
         request.failed(Streamable.STREAMABLE);
         ack.error("executeStreamTask() threw an exception", e);
         log.error("executeStreamTask() returned an exception", e);
      }

      if (clustered != null && cacheInformation != null) {
         log.infof("%d nodes were used. %d entries on this node", clustered.getMembers().size(), cacheInformation
            .getCache(cacheName).getLocallyStoredSize());
      }
      log.info("--------------------");

      return ack;
   }

   public interface StreamFunction extends Function<Stream, Object> {

      /**
       * @return result of the stream task in form of String
       */
      String getPrintableResult();

      /**
       * @return number of results produced by stream task
       */
      long getResultCount();
   }

   private static class StreamStageAck extends DistStageAck {
      private Statistics stats;
      private String numberOfResultKeys;

      private StreamStageAck(SlaveState slaveState) {
         super(slaveState);
      }

      public void setStats(Statistics stats) {
         this.stats = stats;
      }

      public void setNumberOfResultKeys(long numberOfResultKeys) {
         this.numberOfResultKeys = Long.toString(numberOfResultKeys);
      }
   }
}
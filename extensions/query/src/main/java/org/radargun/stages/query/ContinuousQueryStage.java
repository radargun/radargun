package org.radargun.stages.query;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.radargun.DistStageAck;
import org.radargun.Operation;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.PropertyDelegate;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.SlaveState;
import org.radargun.stats.BasicOperationStats;
import org.radargun.stats.Statistics;
import org.radargun.stats.SynchronizedStatistics;
import org.radargun.traits.ContinuousQuery;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Query;
import org.radargun.traits.Queryable;
import org.radargun.utils.TimeService;
import org.radargun.utils.Timestamped;

/**
 * Registers continuous query for given query and cache.
 *
 * @author vjuranek
 */
@Stage(doc = "Benchmark operations performance with enabled/disabled continuous query.")
public class ContinuousQueryStage extends AbstractDistStage {

   private static final String CQ_TEST_NAME = "ContinuousQueryTest";

   @Property(doc = "Name of the test as used for reporting. Default is 'Test'.")
   public String testName = "Test";

   @Property(doc = "Cache name with which continuous query should registered. Default is null, i.e. default cache.")
   public String cacheName = null;

   @Property(doc = "If multiple queries are used, specifies, if statistics should be merged in one or each CQ should keep its own statistics. Default it false.")
   public boolean mergeCq = false;

   @Property(doc = "Allows to reset statistics at the begining of the stage. Default is false.")
   public boolean resetStats = false;

   @Property(doc = "Allows to remove continuous query. Default is false.")
   public boolean remove = false;

   @PropertyDelegate
   public QueryConfiguration query = new QueryConfiguration();

   @InjectTrait
   private ContinuousQuery continuousQueryTrait;

   @InjectTrait
   private Queryable queryable;

   private Map<String, SynchronizedStatistics> statistics;

   @Override
   public DistStageAck executeOnSlave() {
      String statsKey = mergeCq ? CQ_TEST_NAME + ".Stats" : testName + ".Stats";

      statistics = (Map<String, SynchronizedStatistics>) slaveState.get(statsKey);
      if (statistics == null) {
         statistics = new HashMap<String, SynchronizedStatistics>();
         slaveState.put(statsKey, statistics);
      }
      if (!statistics.containsKey(statsKey)) {
         statistics.put(statsKey, new SynchronizedStatistics(new BasicOperationStats()));
      } else if (resetStats) {
         statistics.get(statsKey).reset();
      }

      if (!remove) {
         registerCQ(slaveState);
      } else {
         unregisterCQ(slaveState);
      }

      return new ContinuousQueryAck(slaveState, statistics.get(statsKey).snapshot(true));
   }

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMaster(acks);
      if (result.isError())
         return result;

      String testKey = mergeCq ? CQ_TEST_NAME : testName;
      Report.Test test = createTest(testKey, null);
      if (test != null) {
         int testIteration = (mergeCq || remove) ? 0 : test.getIterations().size(); // when merging or removing CQ, don't consider iterations

         for (ContinuousQueryAck ack : instancesOf(acks, ContinuousQueryAck.class)) {
            if (ack.stats != null)
               test.addStatistics(testIteration, ack.getSlaveIndex(), Collections.singletonList(ack.stats));
         }
      }
      return StageResult.SUCCESS;
   }

   protected Report.Test createTest(String testKey, String iterationName) {
      if (testKey == null || testKey.isEmpty()) {
         log.warn("No test name - results are not recorded");
         return null;
      } else {
         Report report = masterState.getReport();
         return report.createTest(testKey, iterationName, true);
      }
   }

   private void registerCQ(SlaveState slaveState) {
      if ((query.projectionAggregated != null && !query.projectionAggregated.isEmpty()) ||
            (query.groupBy != null && query.groupBy.length != 0) ||
            (query.orderByAggregatedColumns != null && !query.orderByAggregatedColumns.isEmpty())) {
         throw new IllegalStateException("Aggregations are not supported in continuous queries!");
      }
      Query q = QueryBase.constructBuilder(queryable, query).build();
      slaveState.put(ContinuousQuery.QUERY, q);

      ContinuousQuery.Listener cqListener = new ContinuousQuery.Listener() {
         private final String statsKey = mergeCq ? CQ_TEST_NAME + ".Stats" : testName + ".Stats";

         @Override
         public void onEntryJoined(Object key, Object value) {
            record(key, ContinuousQuery.ENTRY_JOINED);
            log.trace("Entry joined " + key + " -> " + value);
         }

         @Override
         public void onEntryLeft(Object key) {
            record(key, ContinuousQuery.ENTRY_LEFT);
            log.trace("Entry left " + key);
         }

         private void record(Object key, Operation operation) {
            if (key instanceof Timestamped) {
               SynchronizedStatistics stats = statistics.get(statsKey);
               stats.message()
                     .times(((Timestamped) key).getTimestamp(), TimeService.currentTimeMillis())
                     .record(operation);
            }
         }
      };

      Map<String, ContinuousQuery.ListenerReference> listeners = (Map<String, ContinuousQuery.ListenerReference>) slaveState
         .get(ContinuousQuery.LISTENERS);
      if (listeners == null) {
         listeners = new HashMap<>();
         slaveState.put(ContinuousQuery.LISTENERS, listeners);
      }
      ContinuousQuery.ListenerReference ref = continuousQueryTrait.createContinuousQuery(cacheName, q, cqListener);
      listeners.put(testName, ref);
   }

   public void unregisterCQ(SlaveState slaveState) {
      Map<String, ContinuousQuery.ListenerReference> listeners = (Map<String, ContinuousQuery.ListenerReference>) slaveState
         .get(ContinuousQuery.LISTENERS);
      if (listeners != null && listeners.containsKey(testName)) {
         ContinuousQuery.ListenerReference ref = listeners.remove(testName);
         continuousQueryTrait.removeContinuousQuery(cacheName, ref);
      }
   }

   private static class ContinuousQueryAck extends DistStageAck {
      public final Statistics stats;

      public ContinuousQueryAck(SlaveState slaveState, Statistics stats) {
         super(slaveState);
         this.stats = stats;
      }
   }
}

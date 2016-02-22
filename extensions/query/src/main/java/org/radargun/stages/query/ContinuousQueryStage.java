package org.radargun.stages.query;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.PropertyDelegate;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.SlaveState;
import org.radargun.stats.DefaultOperationStats;
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
   protected String testName = "Test";

   @Property(doc = "Cache name with which continuous query should registered. Default is null, i.e. default cache.")
   private String cacheName = null;

   @Property(doc = "If multiple queries are used, specifies, if statistics should be merged in one or each CQ should keep its own statistics. Default it false.")
   private boolean mergeCq = false;

   @Property(doc = "Allows to reset statistics at the begining of the stage. Default is false.")
   private boolean resetStats = false;

   @Property(doc = "Allows to remove continuous query. Default is false.")
   protected boolean remove = false;

   @PropertyDelegate
   QueryConfiguration query = new QueryConfiguration();
   ;

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
         statistics.put(statsKey, new SynchronizedStatistics(new DefaultOperationStats()));
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
      Class<?> clazz;
      try {
         clazz = slaveState.getClass().getClassLoader().loadClass(query.clazz);
      } catch (ClassNotFoundException e) {
         throw new IllegalArgumentException("Cannot load class " + query.clazz, e);
      }

      Query.Builder builder = queryable.getBuilder(null, clazz);
      for (Condition condition : query.conditions) {
         condition.apply(builder);
      }
      if (query.orderBy != null) {
         for (OrderBy se : query.orderBy) {
            builder.orderBy(se.attribute, se.asc ? Query.SortOrder.ASCENDING : Query.SortOrder.DESCENDING);
         }
      }
      if (query.projection != null) {
         builder.projection(query.projection);
      }
      if (query.offset >= 0) {
         builder.offset(query.offset);
      }
      if (query.limit >= 0) {
         builder.limit(query.limit);
      }
      Query query = builder.build();
      slaveState.put(ContinuousQuery.QUERY, query);

      ContinuousQuery.ContinuousQueryListener cqListener = new ContinuousQuery.ContinuousQueryListener() {
         private final String statsKey = mergeCq ? CQ_TEST_NAME + ".Stats" : testName + ".Stats";

         @Override
         public void onEntryJoined(Object key, Object value) {
            statistics.get(statsKey).registerRequest(getResponseTime(key), ContinuousQuery.ENTRY_JOINED);
            log.trace("Entry joined " + key + " -> " + value);
         }

         @Override
         public void onEntryLeft(Object key) {
            statistics.get(statsKey).registerRequest(getResponseTime(key), ContinuousQuery.ENTRY_LEFT);
            log.trace("Entry left " + key);
         }
      };

      Map<String, ContinuousQuery.ContinuousQueryListener> listeners = (Map<String, ContinuousQuery.ContinuousQueryListener>) slaveState
         .get(ContinuousQuery.LISTENERS);
      if (listeners == null) {
         listeners = new HashMap<>();
         slaveState.put(ContinuousQuery.LISTENERS, listeners);
      }
      listeners.put(testName, cqListener);

      continuousQueryTrait.createContinuousQuery(cacheName, query, cqListener);
   }

   public void unregisterCQ(SlaveState slaveState) {
      Map<String, ContinuousQuery.ContinuousQueryListener> listeners = (Map<String, ContinuousQuery.ContinuousQueryListener>) slaveState
         .get(ContinuousQuery.LISTENERS);
      if (listeners != null && listeners.containsKey(testName)) {
         ContinuousQuery.ContinuousQueryListener cqListener = listeners.get(testName);
         listeners.remove(testName);
         continuousQueryTrait.removeContinuousQuery(cacheName, cqListener);
      }
   }

   private long getResponseTime(Object key) {
      if (key instanceof Timestamped) {
         return (TimeUnit.MILLISECONDS.toNanos(TimeService.currentTimeMillis() - ((Timestamped) key).getTimestamp()));
      }
      return 0; //latency of event arrival is not measured
   }

   private static class ContinuousQueryAck extends DistStageAck {
      public final Statistics stats;

      public ContinuousQueryAck(SlaveState slaveState, Statistics stats) {
         super(slaveState);
         this.stats = stats;
      }
   }
}

package org.radargun.stages.query;

import java.util.List;
import java.util.Map;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.PropertyDelegate;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.test.TransactionMode;
import org.radargun.stages.test.legacy.LegacyStressor;
import org.radargun.stages.test.legacy.LegacyTestStage;
import org.radargun.stages.test.legacy.OperationLogic;
import org.radargun.state.SlaveState;
import org.radargun.stats.Statistics;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.InternalsExposition;
import org.radargun.traits.Queryable;
import org.radargun.utils.Projections;

/**
 * This stage was refactored out to {@link org.radargun.stages.query} package in order
 * to make that code reusable with different threading models.
 */
@Stage(doc = "Stage which executes a query.")
public class QueryStage extends LegacyTestStage {
   @PropertyDelegate
   public QueryConfiguration query = new QueryConfiguration();

   @PropertyDelegate
   public QueryBase base = new QueryBase();

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private Queryable queryable;

   @InjectTrait
   private InternalsExposition internalsExposition;

   @Override
   public OperationLogic getLogic() {
      boolean useTxs = useTransactions == TransactionMode.ALWAYS ? true : useTransactions == TransactionMode.NEVER ? false : transactional != null;
      return new QueryLogic(base, queryable, useTxs);
   }

   @Override
   public void init() {
      base.init(queryable, query);
   }

   @Override
   protected DistStageAck newStatisticsAck(List<LegacyStressor> stressors) {
      QueryBase.Data data = base.createQueryData(internalsExposition);
      return new QueryAck(slaveState, gatherResults(stressors, new StatisticsResultRetriever()), data);
   }

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMaster(acks);
      if (result.isError()) return result;

      Map<Integer, QueryBase.Data> results = Projections.asMap(Projections.instancesOf(acks, QueryAck.class),
            new Projections.Func<QueryAck, Integer>() {
         @Override
         public Integer project(QueryAck ack) {
            return ack.getSlaveIndex();
         }
      }, new Projections.Func<QueryAck, QueryBase.Data>() {
         @Override
         public QueryBase.Data project(QueryAck ack) {
            return ack.data;
         }
      });
      Report.Test test = getTest(true); // the test was already created in super.processAckOnMaster

      base.checkAndRecordResults(results, test, getTestIteration());
      return result;
   }

   protected static class QueryAck extends StatisticsAck {
      private final QueryBase.Data data;

      public QueryAck(SlaveState slaveState, List<List<Statistics>> iterations, QueryBase.Data data) {
         super(slaveState, iterations);
         this.data = data;
      }
   }
}

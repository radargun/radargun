package org.radargun.stages.query;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.PropertyDelegate;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.test.OperationLogic;
import org.radargun.stages.test.Stressor;
import org.radargun.stages.test.TestStage;
import org.radargun.stages.test.TransactionMode;
import org.radargun.state.WorkerState;
import org.radargun.stats.Statistics;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.InternalsExposition;
import org.radargun.traits.Queryable;

/**
 * This stage was refactored out to {@link org.radargun.stages.query} package in order
 * to make that code reusable with different threading models.
 */
@Stage(doc = "Stage which executes a query created from a DSL.")
public class QueryStage extends TestStage {
   @PropertyDelegate
   public QueryConfiguration query = new QueryConfiguration();

   @PropertyDelegate
   public QueryBase base = new QueryBase();

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private Queryable queryable;

   @InjectTrait
   private InternalsExposition internalsExposition;

   @Override
   public OperationLogic createLogic() {
      boolean useTxs = useTransactions == TransactionMode.ALWAYS ? true : useTransactions == TransactionMode.NEVER ? false : useTransactions(null);
      return new QueryLogic(base, queryable, useTxs);
   }

   @Override
   public void init() {
      base.init(queryable, query);
   }

   @Override
   protected DistStageAck newStatisticsAck(List<Stressor> stressors) {
      QueryBase.Data data = base.createQueryData(internalsExposition);
      return new QueryAck(workerState, gatherResults(stressors, new StatisticsResultRetriever()), data);
   }

   @Override
   public StageResult processAckOnMain(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMain(acks);
      if (result.isError()) return result;

      Map<Integer, QueryBase.Data> results = acks.stream().filter(QueryAck.class::isInstance).collect(
            Collectors.toMap(ack -> ack.getWorkerIndex(), ack -> ((QueryAck) ack).data));
      Report.Test test = getTest(true); // the test was already created in super.processAckOnMain

      base.checkAndRecordResults(results, test, getTestIteration());
      return result;
   }

   protected static class QueryAck extends StatisticsAck {
      private final QueryBase.Data data;

      public QueryAck(WorkerState workerState, List<Statistics> statistics, QueryBase.Data data) {
         super(workerState, statistics, null);
         this.data = data;
      }
   }
}

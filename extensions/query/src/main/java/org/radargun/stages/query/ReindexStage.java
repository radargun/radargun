package org.radargun.stages.query;

import java.util.Collections;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.SlaveState;
import org.radargun.stats.BasicOperationStats;
import org.radargun.stats.BasicStatistics;
import org.radargun.stats.Statistics;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Queryable;

/**
 * Just runs reindex.
 */
@Stage(doc = "Runs Queryable.reindex()")
public class ReindexStage extends AbstractDistStage {
   @Property(doc = "Container (e.g. cache or DB table) which should be reindex. Default is the default container.")
   private String container;

   @Property(doc = "Test under which performance of reindexing should be recorded. Default is 'reindex'.")
   private String test = "reindex";

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private Queryable queryable;

   @Override
   public DistStageAck executeOnSlave() {
      Statistics stats = new BasicStatistics(new BasicOperationStats());
      stats.begin();
      stats.startRequest().exec(Queryable.REINDEX, () -> queryable.reindex(container));
      stats.end();
      return new StatisticsAck(slaveState, stats);
   }

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMaster(acks);
      if (result.isError()) return result;

      Report.Test test = masterState.getReport().createTest(this.test, null, false);
      for (DistStageAck ack : acks) {
         if (ack instanceof StatisticsAck) {
            test.addStatistics(0, ack.getSlaveIndex(), Collections.singletonList(((StatisticsAck) ack).stats));
         }
      }
      return StageResult.SUCCESS;
   }

   private static class StatisticsAck extends DistStageAck {
      private final Statistics stats;

      public StatisticsAck(SlaveState slaveState, Statistics stats) {
         super(slaveState);
         this.stats = stats;
      }
   }
}

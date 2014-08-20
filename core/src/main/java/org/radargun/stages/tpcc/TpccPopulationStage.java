
package org.radargun.stages.tpcc;

import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.SlaveState;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.InjectTrait;
import org.radargun.utils.Projections;

/**
 * This stage shuld be run before the <b>TpccBenchmarkStage</b>. It will perform the population of
 * <b>numWarehouses</b> warehouses in cache. Note: this stage won't clear the added data from
 * slave.
 * <pre>
 * Params:
 *       - numWarehouses : the number of warehouses to be populated.
 *       - cLastMask : the mask used to generate non-uniformly distributed random customer last names.
 *       - olIdMask : mask used to generate non-uniformly distributed random item numbers.
 *       - cIdMask : mask used to generate non-uniformly distributed random customer numbers.
 * </pre>
 *
 * @author Sebastiano Peluso &lt;peluso@gsd.inesc-id.pt, peluso@dis.uniroma1.it&gt;
 */
@Stage(doc = "This stage shuld be run before the TpccBenchmarkStage.")
public class TpccPopulationStage extends AbstractDistStage {
   
   @Property(doc = "Number of Warehouses. Default is 1.")
   private int numWarehouses = 1;

   @Property(doc = "Mask used to generate non-uniformly distributed random customer last names. Default is 255.")
   private long cLastMask = 255;

   @Property(doc = "Mask used to generate non-uniformly distributed random item numbers. Default is 8191.")
   private long olIdMask = 8191;
   
   @Property(doc = "Mask used to generate non-uniformly distributed random customer numbers. Default is 1023.")
   private long cIdMask = 1023;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private BasicOperations basicOperations;

   public DistStageAck executeOnSlave() {
      if (!isServiceRunnning()) {
         log.info("Not executing any test as the service is not running on this slave ");
         return successfulResponse();
      }
      long startTime = System.currentTimeMillis();
      try {
         log.info("Performing Population Operations");
         new TpccPopulation(basicOperations.getCache(null), numWarehouses, slaveState.getSlaveIndex(), slaveState.getClusterSize(), cLastMask, olIdMask, cIdMask);
      } catch (Exception e) {
         log.warn("Received exception during cache population" + e.getMessage());
      }
      long duration = System.currentTimeMillis() - startTime;
      log.info("The population took: " + (duration / 1000) + " seconds.");
      return new DurationAck(slaveState, duration);
   }

   public boolean processAckOnMaster(List<DistStageAck> acks) {
      if (!super.processAckOnMaster(acks)) return false;
      logDurationInfo(acks);
      for (DurationAck ack : Projections.instancesOf(acks, DurationAck.class)) {
         if (log.isTraceEnabled()) {
            log.trace("Tpcc population on slave " + ack.getSlaveIndex() + " finished in " + ack.duration + " millis.");
         }
      }
      return true;
   }

   private static class DurationAck extends DistStageAck {
      final long duration;

      private DurationAck(SlaveState slaveState, long duration) {
         super(slaveState);
         this.duration = duration;
      }
   }
}

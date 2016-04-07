package org.radargun.stats.representation;

import java.util.concurrent.TimeUnit;

import org.radargun.config.DefinitionElement;
import org.radargun.stats.Statistics;

/**
 * Number of operations per second. May be imprecise if the merged periods are not identical.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class OperationThroughput {
   /**
    * Actual throughput of the executed operations, including failed ones.
    */
   public final double gross;
   /**
    * Actual throughput of the executed operations, without errors.
    */
   public final double net;

   public OperationThroughput(double gross, double net) {
      this.gross = gross;
      this.net = net;
   }

   public static OperationThroughput compute(long requests, long errors, Statistics ownerStatistics) {
      long duration = ownerStatistics.getEnd() - ownerStatistics.getBegin();
      if (duration == 0) return null;
      return new OperationThroughput(TimeUnit.SECONDS.toMillis(1) * (double) requests / duration,
         TimeUnit.SECONDS.toMillis(1) * (double) (requests - errors) / duration);
   }

   @DefinitionElement(name = "throughput-gross", doc = "Retrieve gross throughput (counting errors)")
   public static class GrossThroughput extends RepresentationType {
      @Override
      public double getValue(Statistics statistics, String operation, long duration) {
         OperationThroughput throughput = statistics.getRepresentation(operation, OperationThroughput.class, duration);
         if (throughput == null) throw new IllegalArgumentException("Cannot retrieve throughput from " + operation);
         return throughput.gross;
      }
   }

   @DefinitionElement(name = "throughput-net", doc = "Retrieve net throughput (not counting errors).")
   public static class NetThroughput extends RepresentationType {
      @Override
      public double getValue(Statistics statistics, String operation, long duration) {
         OperationThroughput throughput = statistics.getRepresentation(operation, OperationThroughput.class, duration);
         if (throughput == null) throw new IllegalArgumentException("Cannot retrieve throughput from " + operation);
         return throughput.net;
      }
   }

   public static class Series extends AbstractSeries<OperationThroughput> {
      static {
         AbstractSeries.register(Series.class, OperationThroughput.class);
      }

      public Series(long startTime, long period, OperationThroughput[] samples) {
         super(startTime, period, samples);
      }
   }
}

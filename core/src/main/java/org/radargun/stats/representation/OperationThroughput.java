package org.radargun.stats.representation;

import java.util.concurrent.TimeUnit;

import org.radargun.config.DefinitionElement;
import org.radargun.stats.OperationStats;
import org.radargun.utils.Utils;

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

   public static OperationThroughput compute(long requests, long errors, Object[] args) {
      long duration = getDuration(args);
      if (duration == 0) return null;
      return new OperationThroughput(TimeUnit.SECONDS.toNanos(1) * (double) requests / duration,
            TimeUnit.SECONDS.toNanos(1) * (double) (requests - errors) / duration);
   }

   public static long getDuration(Object[] args) {
      long duration = Utils.getArg(args, 0, Long.class);
      if (duration < 0) throw new IllegalArgumentException(String.valueOf(duration));
      return duration;
   }

   @DefinitionElement(name = "throughput-gross", doc = "Retrieve gross throughput (counting errors)")
   public static class GrossThroughput extends RepresentationType {
      @Override
      public double getValue(OperationStats stats, long duration) {
         OperationThroughput throughput = stats.getRepresentation(OperationThroughput.class, duration);
         if (throughput == null) throw new IllegalArgumentException("Cannot retrieve throughput from " + stats);
         return throughput.gross;
      }
   }

   @DefinitionElement(name = "throughput-net", doc = "Retrieve net throughput (not counting errors).")
   public static class NetThroughput extends RepresentationType {
      @Override
      public double getValue(OperationStats stats, long duration) {
         OperationThroughput throughput = stats.getRepresentation(OperationThroughput.class, duration);
         if (throughput == null) throw new IllegalArgumentException("Cannot retrieve throughput from " + stats);
         return throughput.net;
      }
   }
}

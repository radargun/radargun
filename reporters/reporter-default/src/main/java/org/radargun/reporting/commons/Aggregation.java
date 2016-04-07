package org.radargun.reporting.commons;

import java.util.List;

import org.radargun.reporting.Report;
import org.radargun.stats.Statistics;
import org.radargun.stats.representation.DefaultOutcome;

/**
 * Aggregates statistics information from test
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @author Vitalii Chepeliuk &lt;vchepeli@redhat.com&gt;
 * @since 2.0
 */
public class Aggregation {
   public final List<Statistics> nodeStats;
   public final Statistics totalStats;
   public final List<Integer> nodeThreads;
   public final int totalThreads;
   public final Report.TestIteration iteration;

   public Aggregation(List<Statistics> nodeStats, List<Integer> nodeThreads, Statistics totalStats,
                      int totalThreads, Report.TestIteration iteration) {
      this.nodeStats = nodeStats;
      this.nodeThreads = nodeThreads;
      this.totalStats = totalStats;
      this.totalThreads = totalThreads;
      this.iteration = iteration;
   }

   public boolean anySuspect(String operation) {
      for (int i = 0; i < nodeStats.size(); ++i) {
         if (isSuspect(i, operation)) {
            return true;
         }
      }
      return false;
   }

   public boolean isSuspect(int node, String operation) {
      Statistics ns;
      if (node >= nodeStats.size() || (ns = nodeStats.get(node)) == null) {
         return false;
      }
      DefaultOutcome ndo = ns.getRepresentation(operation, DefaultOutcome.class);
      DefaultOutcome tdo = totalStats.getRepresentation(operation, DefaultOutcome.class);
      if (ndo == null) {
         return tdo != null;
      }
      double requestsAverage = getRequestsAverage(operation);
      return ndo.requests < requestsAverage * 4 / 5 || requestsAverage > tdo.requests * 5 / 4;
   }

   private double getRequestsAverage(String operation) {
      long requests = 0;
      int slaveStatsCount = 0;
      for (Statistics ns : nodeStats) {
         if (ns == null) continue;
         DefaultOutcome defaultOutcome = ns.getRepresentation(operation, DefaultOutcome.class);
         if (defaultOutcome != null) {
            requests += defaultOutcome.requests;
            slaveStatsCount++;
         }
      }
      return slaveStatsCount > 0 ? requests / slaveStatsCount : 0;
   }
}
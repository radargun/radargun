package org.radargun.reporting.commons;

import org.radargun.stats.OperationStats;
import org.radargun.stats.Statistics;
import org.radargun.stats.representation.DefaultOutcome;

import java.util.List;

/**
 * Aggregates statistics information from test
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @author Vitalii Chepeliuk &lt;vchepeli@redhat.com&gt;
 * @since 2.0
 */
public class Aggregation {
   final List<Statistics> nodeStats;
   final Statistics totalStats;
   final List<Integer> nodeThreads;
   final int totalThreads;
   final String iterationName;
   final String iterationValue;

   public Aggregation(List<Statistics> nodeStats, List<Integer> nodeThreads, Statistics totalStats,
                      int totalThreads, String iterationName, String iterationValue) {
      this.nodeStats = nodeStats;
      this.nodeThreads = nodeThreads;
      this.totalStats = totalStats;
      this.totalThreads = totalThreads;
      this.iterationName = iterationName;
      this.iterationValue = iterationValue;
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
      OperationStats nos = ns.getOperationsStats().get(operation);
      OperationStats tos = totalStats.getOperationsStats().get(operation);
      if (nos == null) {
         return tos != null;
      }
      DefaultOutcome ndo = nos.getRepresentation(DefaultOutcome.class);
      DefaultOutcome tdo = tos.getRepresentation(DefaultOutcome.class);
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
         OperationStats operationStats = ns.getOperationsStats().get(operation);
         if (operationStats != null) {
            DefaultOutcome defaultOutcome = operationStats.getRepresentation(DefaultOutcome.class);
            if (defaultOutcome != null) {
               requests += defaultOutcome.requests;
               slaveStatsCount++;
            }
         }
      }
      return slaveStatsCount > 0 ? requests / slaveStatsCount : 0;
   }

   public List<Statistics> nodeStats() {
      return nodeStats;
   }

   public Statistics totalStats() {
      return totalStats;
   }

   public List<Integer> nodeThreads() {
      return nodeThreads;
   }

   public int totalThreads() {
      return totalThreads;
   }

   public String iterationName() {
      return iterationName;
   }

   public String iterationValue() {
      return iterationValue;
   }
}
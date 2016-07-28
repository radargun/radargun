package org.radargun.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.radargun.Operation;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;

/**
 * Implements the Statistics interface using provided {@link OperationStats}.
 */
@DefinitionElement(name = "basic", doc = "Statistics with fixed memory footprint for each operation statistics.")
public class BasicStatistics extends IntervalStatistics {
   private static final OperationStats[] EMPTY_ARRAY = new OperationStats[0];
   private transient OperationStats[] operationStats = EMPTY_ARRAY;
   private Map<String, OperationStats> operationStatsMap = new HashMap<String, OperationStats>();

   @Property(name = "operationStats", doc = "Operation statistics prototype.", complexConverter = OperationStats.Converter.class)
   protected OperationStats prototype = new BasicOperationStats();

   public BasicStatistics() {
   }

   public BasicStatistics(OperationStats prototype) {
      this.prototype = prototype;
   }

   protected OperationStats createOperationStats(int operationId) {
      return prototype.newInstance();
   }

   public Statistics newInstance() {
      return new BasicStatistics(prototype);
   }

   @Override
   public void reset() {
      operationStatsMap.clear();
      for (int i = 0; i < operationStats.length; ++i) {
         operationStats[i] = createOperationStats(i);
         operationStatsMap.put(Operation.getById(i).name, operationStats[i]);
      }
      begin();
   }

   @Override
   public void record(Request request, Operation operation) {
      ensure(operation.id);
      OperationStats stats = operationStats[operation.id];
      stats.record(request);
   }

   @Override
   public void record(Message message, Operation operation) {
      ensure(operation.id);
      OperationStats stats = operationStats[operation.id];
      stats.record(message);
   }

   @Override
   public void record(RequestSet requestSet, Operation operation) {
      ensure(operation.id);
      OperationStats stats = operationStats[operation.id];
      stats.record(requestSet);
   }

   private void ensure(int operationId) {
      if (operationId >= operationStats.length) {
         OperationStats[] temp = new OperationStats[operationId + 1];
         System.arraycopy(operationStats, 0, temp, 0, operationStats.length);
         operationStats = temp;
      }
      if (operationStats[operationId] == null) {
         OperationStats operationStats = createOperationStats(operationId);
         this.operationStats[operationId] = operationStats;
         operationStatsMap.put(Operation.getById(operationId).name, operationStats);
      }
   }

   @Override
   public Statistics copy() {
      BasicStatistics copy = (BasicStatistics) newInstance();
      copy.merge(this);
      return copy;
   }

   /**
    * Merge otherStats to this. leaves otherStats unchanged.
    *
    * @param otherStats
    */
   @Override
   public void merge(Statistics otherStats) {
      if (!(otherStats instanceof BasicStatistics))
         throw new IllegalArgumentException(otherStats.getClass().getName());
      super.merge(otherStats);
      BasicStatistics stats = (BasicStatistics) otherStats;
      if (operationStats == null) {
         // after deserialization
         operationStats = EMPTY_ARRAY;
      }
      if (stats.operationStats == null) {
         for (Map.Entry<String, OperationStats> entry : stats.operationStatsMap.entrySet()) {
            Operation operation = Operation.getByName(entry.getKey());
            ensure(operation.id);
            if (operationStats[operation.id] == null) {
               operationStats[operation.id] = entry.getValue().copy();
               operationStatsMap.put(entry.getKey(), operationStats[operation.id]);
            } else {
               operationStats[operation.id].merge(entry.getValue());
            }
         }
      } else {
         ensure(Math.max(0, stats.operationStats.length - 1));
         for (int i = 0; i < stats.operationStats.length; ++i) {
            if (stats.operationStats[i] == null) {
               continue;
            } else if (operationStats[i] == null) {
               operationStats[i] = stats.operationStats[i].copy();
               operationStatsMap.put(Operation.getById(i).name, operationStats[i]);
            } else {
               operationStats[i].merge(stats.operationStats[i]);
            }
         }
      }
   }

   @Override
   public Set<String> getOperations() {
      return operationStatsMap.keySet();
   }

   @Override
   public OperationStats getOperationStats(String operation) {
      return operationStatsMap.get(operation);
   }

   @Override
   public <T> T getRepresentation(String operationName, Class<T> clazz, Object... args) {
      OperationStats operationStats = operationStatsMap.get(operationName);
      if (operationStats != null) {
         return operationStats.getRepresentation(clazz, this, args);
      } else {
         return prototype.getRepresentation(clazz, this, args);
      }
   }

   @Override
   public String toString() {
      return super.toString() + "{" + operationStatsMap + "}";
   }
}

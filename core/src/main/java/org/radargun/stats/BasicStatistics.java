package org.radargun.stats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.radargun.Operation;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;

/**
 * Implements the Statistics interface using provided {@link OperationStats}.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@DefinitionElement(name = "basic", doc = "Statistics with fixed memory footprint for each operation statistics.")
public class BasicStatistics extends IntervalStatistics {
   private static final OperationStats[] EMPTY_ARRAY = new OperationStats[0];
   private transient OperationStats[] operationStats = EMPTY_ARRAY;
   private Map<String, OperationStats> operationStatsMap = new HashMap<>();

   private Map<String, Set<Operation>> groupOperationsMap = new HashMap<>();
   private Map<Operation, String> operationGroupMap = new HashMap<>();

   @Property(name = "operationStats", doc = "Operation statistics prototype.", complexConverter = OperationStats.Converter.class)
   protected OperationStats prototype = new BasicOperationStats();

   public BasicStatistics() {
   }

   public BasicStatistics(OperationStats prototype) {
      this.prototype = prototype;
   }

   protected OperationStats createOperationStats(int operationId) {
      return prototype.copy();
   }

   public Statistics newInstance() {
      return new BasicStatistics(prototype);
   }

   @Override
   public void registerOperationsGroup(String name, Set<Operation> operations) {
      if (groupOperationsMap.containsKey(name)) {
         return;
      }
      for (Map.Entry<String, Set<Operation>> entry : groupOperationsMap.entrySet()) {
         for (Operation operation : operations) {
            if (entry.getValue().contains(operation)) {
               return;
            }

         }
      }
      Set<Operation> updatedIdOperation = new HashSet<>();
      for (Operation operation : operations) {
         // This ensures the IDs match
         operation = Operation.getByName(operation.name);
         updatedIdOperation.add(operation);

         ensure(operation.id);
         operationGroupMap.put(operation, name);
      }
      groupOperationsMap.put(name, updatedIdOperation);
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

   /**
    * Ensures that there is entry in operationStats with this ID
    * If there isn't create it
    * @param operationId
    */
   private void ensure(int operationId) {
      if (operationStats == null) {
         operationStats = EMPTY_ARRAY;
      }
      if (operationId >= operationStats.length) {
         OperationStats[] temp = new OperationStats[operationId + 1];
         System.arraycopy(operationStats, 0, temp, 0, operationStats.length);
         operationStats = temp;
      }
      if (operationStats[operationId] == null) {
         if (operationStatsMap.get(Operation.getById(operationId).name) == null) {
            OperationStats operationStats = createOperationStats(operationId);
            this.operationStats[operationId] = operationStats;
            operationStatsMap.put(Operation.getById(operationId).name, operationStats);
         } else {
            operationStats[operationId] = operationStatsMap.get(Operation.getById(operationId).name);
         }
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
   public List<Map<String, OperationStats>> getOperationStatsForGroups() {
      Map<String, OperationStats> result = new HashMap<>(groupOperationsMap.size());
      for (Map.Entry<String, Set<Operation>> entry : groupOperationsMap.entrySet()) {
         OperationStats mergedOperationStats = null;
         for (Operation operation : entry.getValue()) {
            OperationStats os = operationStats[operation.id];
            if (mergedOperationStats == null) {
               mergedOperationStats = os.copy();
            } else {
               mergedOperationStats.merge(os);
            }
         }
         if (!mergedOperationStats.isEmpty()) {
            result.put(entry.getKey(), mergedOperationStats);
         }
      }
      List<Map<String, OperationStats>> list = new ArrayList<>();
      list.add(result);
      return list;
   }

   @Override
   public List<Map<String, OperationStats>> getOperationsStats() {
      List<Map<String, OperationStats>> list = new ArrayList<>();
      list.add(operationStatsMap);
      return list;
   }

   @Override
   public String getOperationsGroup(Operation operation) {
      return operationGroupMap.get(operation);
   }

   @Override
   public Map<String, Set<Operation>> getGroupOperationsMap() {
      return groupOperationsMap;
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
      if (operationStats == null) {
         // it's always going to be the first element in Basic Statistics
         operationStats = getOperationStatsForGroups().get(0).get(operationName);
      }
      if (operationStats == null) {
         return prototype.getRepresentation(clazz, this, args);
      }
      return operationStats.getRepresentation(clazz, this, args);
   }

   @Override
   public String toString() {
      return super.toString() + "{" + operationStatsMap + "}";
   }
}

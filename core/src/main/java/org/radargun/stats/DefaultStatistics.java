package org.radargun.stats;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
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
@DefinitionElement(name = "default", doc = "Statistics with the same implementation of operation statistics.")
public class DefaultStatistics extends IntervalStatistics {
   private transient OperationStats[] operationStats = new OperationStats[0];
   private Map<String, OperationStats> operationStatsMap = new HashMap<String, OperationStats>();

   private Map<String, Set<Operation>> groupOperationsMap = new HashMap<>();
   private Map<Operation, String> operationGroupMap = new HashMap<>();

   @Property(name = "operationStats", doc = "Operation statistics prototype.", complexConverter = OperationStats.Converter.class)
   protected OperationStats prototype = new DefaultOperationStats();

   @Override
   public Map<String, Set<Operation>> getGroupOperationsMap() {
      return groupOperationsMap;
   }

   public static Statistics merge(Collection<Statistics> set) {
      if (set.size() == 0) {
         return null;
      }
      Iterator<Statistics> elems = set.iterator();
      Statistics res = elems.next().copy();
      while (elems.hasNext()) {
         res.merge(elems.next());
      }
      return res;
   }

   public DefaultStatistics() {
   }

   public DefaultStatistics(OperationStats prototype) {
      this.prototype = prototype;
   }

   protected OperationStats createOperationStats(int operationId) {
      return prototype.copy();
   }

   public Statistics newInstance() {
      return new DefaultStatistics(prototype);
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
   public void registerRequest(long responseTime, Operation operation) {
      ensure(operation.id + 1);
      OperationStats stats = operationStats[operation.id];
      stats.registerRequest(responseTime);
   }

   @Override
   public void registerError(long responseTime, Operation operation) {
      ensure(operation.id + 1);
      OperationStats stats = operationStats[operation.id];
      stats.registerError(responseTime);
   }

   private void ensure(int operationCount) {
      if (operationCount <= operationStats.length) {
         return;
      }
      OperationStats[] temp = new OperationStats[operationCount];
      System.arraycopy(operationStats, 0, temp, 0, operationStats.length);
      for (int i = operationStats.length; i < operationCount; ++i) {
         temp[i] = createOperationStats(i);
         operationStatsMap.put(Operation.getById(i).name, temp[i]);
      }
      operationStats = temp;
   }

   @Override
   public Statistics copy() {
      DefaultStatistics copy = (DefaultStatistics) newInstance();
      copy.merge(this);
      return copy;
   }

   @Override
   public void registerOperationsGroup(String name, Set<Operation> operations) {
      if (groupOperationsMap.containsKey(name)) {
         throw new IllegalArgumentException("Group with name " + name + " already exists");
      }
      for (Map.Entry<String, Set<Operation>> entry : groupOperationsMap.entrySet()) {
         for (Operation operation : operations) {
            if (entry.getValue().contains(operation)) {
               throw new IllegalArgumentException("Operation " + operation + " is already included in group " + entry.getKey());
            }
         }
      }
      groupOperationsMap.put(name, operations);
      for (Operation operation : operations) {
         ensure(operation.id + 1);
         operationGroupMap.put(operation, name);
      }
   }

   /**
    * Merge otherStats to this. leaves otherStats unchanged.
    *
    * @param otherStats
    */
   @Override
   public void merge(Statistics otherStats) {
      if (!(otherStats instanceof DefaultStatistics)) throw new IllegalArgumentException(otherStats.getClass().getName());
      super.merge(otherStats);
      DefaultStatistics stats = (DefaultStatistics) otherStats;
      if (operationStats == null) {
         // after deserialization
         operationStats = new OperationStats[0];
      }
      if (stats.operationStats == null) {
         for (Map.Entry<String, OperationStats> entry : stats.operationStatsMap.entrySet()) {
            Operation operation = Operation.getByName(entry.getKey());
            ensure(operation.id + 1);
            operationStats[operation.id].merge(entry.getValue());
         }
      } else {
         ensure(stats.operationStats.length);
         for (int i = 0; i < stats.operationStats.length; ++i) {
            operationStats[i].merge(stats.operationStats[i]);
         }
      }
   }

   @Override
   public Map<String, OperationStats> getOperationsStats() {
      return operationStatsMap;
   }

   @Override
   public Map<String, OperationStats> getOperationStatsForGroups() {
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
      return result;
   }

   @Override
   public String getOperationsGroup(Operation operation) {
      return operationGroupMap.get(operation);
   }

   @Override
   public <T> T[] getRepresentations(Class<T> clazz, Object... args) {
      T[] representations = (T[]) Array.newInstance(clazz, operationStats.length);
      for (int i = 0; i < operationStats.length; ++i) {
         representations[i] = operationStats[i].getRepresentation(clazz, args);
      }
      return representations;
   }

   @Override
   public String toString() {
      return super.toString() + "{" + operationStatsMap + "}";
   }
}

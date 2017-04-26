package org.radargun.stats;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.radargun.Operation;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class MultiStatistics implements Statistics {
   protected final Statistics[] internal;

   protected MultiStatistics(Statistics[] internal) {
      if (internal == null || internal.length == 0) {
         throw new IllegalArgumentException(Arrays.toString(internal));
      }
      this.internal = internal;
   }

   @Override
   public void begin() {
      for (Statistics s : internal) {
         s.begin();
      }
   }

   @Override
   public void end() {
      for (Statistics s : internal) {
         s.end();
      }
   }

   @Override
   public void reset() {
      for (Statistics s : internal) {
         s.reset();
      }
   }

   @Override
   public void record(Request request, Operation operation) {
      for (Statistics s : internal) {
         s.record(request, operation);
      }
   }

   @Override
   public void record(Message message, Operation operation) {
      for (Statistics s : internal) {
         s.record(message, operation);
      }
   }

   @Override
   public void record(RequestSet requestSet, Operation operation) {
      for (Statistics s : internal) {
         s.record(requestSet, operation);
      }
   }

   @Override
   public Statistics newInstance() {
      return newInstance(Stream.of(internal).map(s -> s.newInstance()).toArray(Statistics[]::new));
   }

   protected abstract MultiStatistics newInstance(Statistics[] internal);

   @Override
   public Statistics copy() {
      return copy(Stream.of(internal).map(s -> s.copy()).toArray(Statistics[]::new));
   }

   protected abstract MultiStatistics copy(Statistics[] internalCopy);

   @Override
   public void merge(Statistics otherStats) {
      if (!(otherStats instanceof MultiStatistics)) {
         throw new IllegalArgumentException(String.valueOf(otherStats));
      }
      MultiStatistics ms = (MultiStatistics) otherStats;
      if (ms.internal.length != internal.length) {
         throw new IllegalArgumentException("Internal statistics differ: "
            + Arrays.toString(internal) + " vs. " + Arrays.toString(ms.internal));
      }
      for (int i = 0; i < ms.internal.length; ++i) {
         internal[i].merge(ms.internal[i]);
      }
   }

   @Override
   public long getBegin() {
      return internal[0].getBegin();
   }

   @Override
   public long getEnd() {
      return internal[internal.length - 1].getBegin();
   }

   @Override
   public Set<String> getOperations() {
      return Stream.of(internal).flatMap(s -> s.getOperations().stream()).collect(Collectors.toSet());
   }

   @Override
   public <T> T getRepresentation(String operation, Class<T> clazz, Object... args) {
      return Stream.of(internal).map(s -> s.getRepresentation(operation, clazz, args)).filter(r -> r != null).findFirst().orElse(null);
   }

   @Override
   public void registerOperationsGroup(String name, Set<Operation> operations) {
      internal[0].registerOperationsGroup(name, operations);
   }

   @Override
   public String getOperationsGroup(Operation operation) {
      return Stream.of(internal).map(s -> s.getOperationsGroup(operation)).filter(r -> r != null).findFirst().orElse(null);
   }

   @Override
   public List<Map<String, OperationStats>> getOperationStatsForGroups() {
      return Stream.of(internal).map(s -> s.getOperationStatsForGroups()).filter(r -> r != null).flatMap(List::stream).collect(Collectors.toList());
   }

   @Override
   public List<Map<String, OperationStats>> getOperationsStats() {
      return Stream.of(internal).map(s -> s.getOperationsStats()).filter(r -> r != null).flatMap(List::stream).collect(Collectors.toList());
   }

   @Override
   public OperationStats getOperationStats(String operation) {
      return Stream.of(internal).map(s -> s.getOperationStats(operation)).filter(r -> r != null).findFirst().orElse(null);
   }

   @Override
   public Map<String, Set<Operation>> getGroupOperationsMap() {
      return Stream.of(internal).map(s -> s.getGroupOperationsMap()).filter(r -> r != null).findFirst().orElse(null);
   }
}

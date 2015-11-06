package org.radargun.util;


import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Matej Cimbora
 */
public class CoreTraitRepository {

   public static Map<Class<?>, Object> getAllTraits() {
      Map<Class<?>, Object> traitMap = new HashMap<>();
      traitMap.put(org.radargun.traits.Transactional.class, new Transactional());
      traitMap.put(org.radargun.traits.Lifecycle.class, new Lifecycle());
      traitMap.put(org.radargun.traits.InternalsExposition.class, new InternalsExposition());
      traitMap.put(org.radargun.traits.Clustered.class, new Clustered(0));
      traitMap.put(org.radargun.traits.Partitionable.class, new Partitionable());
      return traitMap;
   }

   public interface TxResource {
      void begin();
      void commit();
      void rollback();
   }

   public interface Wrappable {
      TxResource wrap();
   }

   public static class Transactional implements org.radargun.traits.Transactional {

      private final Configuration configuration;
      /**
       * Mode of transactional operations, if true then begin, commit, rollback commands will throw ISE
       */
      private boolean failOperations;

      public Transactional() {
         this.configuration = Configuration.TRANSACTIONAL;
      }

      public Transactional(Configuration configuration) {
         this.configuration = configuration;
      }

      @Override
      public Configuration getConfiguration(String resourceName) {
         return configuration;
      }

      public void setFailOperations(boolean failOperations) {
         this.failOperations = failOperations;
      }

      @Override
      public Transaction getTransaction() {
         return new CoreTraitRepository.Transaction(failOperations);
      }
   }

   private static class Transaction implements org.radargun.traits.Transactional.Transaction {
      private TxResource txResource;
      private boolean failOperations;

      public Transaction() {
      }

      public Transaction(boolean failOperations) {
         this.failOperations = failOperations;
      }

      @Override
      public <T> T wrap(T resource) {
         if (!(resource instanceof Wrappable)) {
            throw new IllegalArgumentException("Only " + Wrappable.class.getSimpleName() + " instances are supported");
         }
         this.txResource = ((Wrappable)resource).wrap();
         return (T) txResource;
      }

      @Override
      public void begin() {
         if (failOperations) {
            throw new IllegalStateException();
         }
         txResource.begin();
      }

      @Override
      public void commit() {
         if (failOperations) {
            throw new IllegalStateException();
         }
         txResource.commit();
      }

      @Override
      public void rollback() {
         if (failOperations) {
            throw new IllegalStateException();
         }
         txResource.rollback();
      }
   }

   public static class Lifecycle implements org.radargun.traits.Lifecycle {

      private volatile boolean running;

      public Lifecycle() {
      }

      public Lifecycle(boolean running) {
         this.running = running;
      }

      @Override
      public void start() {
         running = true;
      }

      @Override
      public void stop() {
         running = false;
      }

      @Override
      public boolean isRunning() {
         return running;
      }
   }

   public static class InternalsExposition implements org.radargun.traits.InternalsExposition {

      private static Map<String, Number> valueMap = new HashMap<>();

      static {
         valueMap.put("test1", 1l);
         valueMap.put("test2", 2l);
      }

      @Override
      public Map<String, Number> getValues() {
         return valueMap;
      }

      @Override
      public String getCustomStatistics(String type) {
         return "test";
      }

      @Override
      public void resetCustomStatistics(String type) {
         // no op
      }
   }

   // TODO make clustered
   public static class Clustered implements org.radargun.traits.Clustered {

      private int index;
      private Date date;

      public Clustered(int index) {
         this.index = index;
         this.date = new Date();
      }

      @Override
      public boolean isCoordinator() {
         return index == 0;
      }

      @Override
      public Collection<Member> getMembers() {
         return Arrays.asList(new Member("foo", true, isCoordinator()));
      }

      @Override
      public List<Membership> getMembershipHistory() {
         return Arrays.asList(new Membership(date, getMembers()));
      }
   }

   public static class Partitionable implements org.radargun.traits.Partitionable {

      private int slaveIndex = -1;
      private Set<Integer> partitionMembers;
      private Set<Integer> initiallyReachable;

      @Override
      public void setMembersInPartition(int slaveIndex, Set<Integer> members) {
         checkSlaveIndex(slaveIndex);
         this.partitionMembers = members;
      }

      @Override
      public void setStartWithReachable(int slaveIndex, Set<Integer> members) {
         checkSlaveIndex(slaveIndex);
         this.initiallyReachable = members;
      }

      private void checkSlaveIndex(int slaveIndex) {
         if (this.slaveIndex == -1) {
            this.slaveIndex = slaveIndex;
         } else {
            if (this.slaveIndex != slaveIndex) {
               throw new IllegalStateException(String.format("Slave indices are not equal, current - %d, new value - %d", slaveIndex, this.slaveIndex));
            }
         }
      }

      public int getSlaveIndex() {
         return slaveIndex;
      }

      public Set<Integer> getPartitionMembers() {
         return partitionMembers == null ? null : Collections.unmodifiableSet(partitionMembers);
      }

      public Set<Integer> getInitiallyReachable() {
         return initiallyReachable == null ? null : Collections.unmodifiableSet(initiallyReachable);
      }
   }

}

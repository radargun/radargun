package org.radargun.stats;

/**
 * Multiplexes calls to several undelying "real" {@link OperationStats}.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class MultiOperationStats implements OperationStats {
   private final OperationStats[] impls;

   public MultiOperationStats(OperationStats... impls) {
      this.impls = impls;
   }

   @Override
   public OperationStats newInstance() {
      OperationStats[] instances = new OperationStats[impls.length];
      for (int i = 0; i < impls.length; ++i)
         instances[i] = impls[i].newInstance();
      return new MultiOperationStats(instances);
   }

   @Override
   public OperationStats copy() {
      OperationStats[] copies = new OperationStats[impls.length];
      for (int i = 0; i < impls.length; ++i)
         copies[i] = impls[i].copy();
      return new MultiOperationStats(copies);
   }

   @Override
   public void merge(OperationStats other) {
      if (!(other instanceof MultiOperationStats)) throw new IllegalArgumentException();
      MultiOperationStats multi = (MultiOperationStats) other;
      for (int i = 0; i < impls.length; ++i)
         impls[i].merge(multi.impls[i]);
   }

   @Override
   public void registerRequest(long responseTime) {
      for (OperationStats impl : impls) {
         impl.registerRequest(responseTime);
      }
   }

   @Override
   public void registerError(long responseTime) {
      for (OperationStats impl : impls) {
         impl.registerError(responseTime);
      }
   }

   @Override
   public <T> T getRepresentation(Class<T> clazz, Object... args) {
      for (OperationStats impl : impls) {
         T representation = impl.getRepresentation(clazz, args);
         if (representation != null) return representation;
      }
      return null;
   }

   @Override
   public boolean isEmpty() {
      for (OperationStats impl : impls) {
         if (!impl.isEmpty()) return false;
      }
      return true;
   }
}

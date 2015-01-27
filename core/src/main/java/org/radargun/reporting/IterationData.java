package org.radargun.reporting;

import java.util.List;

/**
 * Statistics implementing this interface should be expanded to multiple iterations
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface IterationData<T> {
   /**
    * @return Iterations that should this instance expand to.
    */
   List<Iteration<T>> getIterations();

   public static class Iteration<T> {
      public final String name;
      public final T data;

      public Iteration(String name, T data) {
         this.name = name;
         this.data = data;
      }
   }
}

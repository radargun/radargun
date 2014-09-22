package org.radargun.stats;

import java.util.List;

/**
 * Statistics implementing this interface should be expanded to multiple iterations
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface IterationStatistics {
   /**
    * @return Iterations that should this instance expand to.
    */
   List<Iteration> getIterations();

   public static class Iteration {
      public final String name;
      public final Statistics statistics;

      public Iteration(String name, Statistics statistics) {
         this.name = name;
         this.statistics = statistics;
      }
   }
}

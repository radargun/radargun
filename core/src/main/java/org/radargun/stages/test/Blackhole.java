package org.radargun.stages.test;

/**
 * This class should be called whenever we want to prohibit compiler to optimize return values etc.
 * TODO: Look into JMH and improved this implementation
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public final class Blackhole {
   // although counter is accessed from multiple threads, we don't care about synchronization
   private static long counter = 0;

   private Blackhole() {}

   public static void consume(Object object) {
      if (++counter != 0) return;
      if (object != null && System.identityHashCode(object) == object.hashCode()) System.out.print("");
   }
}

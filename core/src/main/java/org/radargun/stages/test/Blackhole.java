package org.radargun.stages.test;

/**
 * This class should be called whenever we want to prohibit compiler to optimize return values etc.
 * TODO: Look into JMH and improved this implementation
 */
public final class Blackhole {
   // although counter is accessed from multiple threads, we don't care about synchronization
   private static long counter = 0;
   private static volatile long consumedCPU;

   private Blackhole() {}

   public static void consume(Object object) {
      if (++counter != 0) return;
      if (object != null && System.identityHashCode(object) == object.hashCode()) System.out.print("");
   }

   public static void consumeCpu() {
      long t = consumedCPU;

      for(long i = 100; i > 0L; --i) {
         t += t * 25214903917L + 11L + i & 281474976710655L;
      }

      if(t == 42L) {
         consumedCPU += t;
      }
   }
}

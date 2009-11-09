package org.cachebench.probes

/**
 * Probe to calc memory footprint
 * @author Manik Surtani
 * @since 4.0
 */

object MemoryFootprintProbe {
   def calculateMemoryFootprint(): Long = {
      var m0 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

      // ensure we see some GC!
      for (i <- 0 to 10) {
         Thread.sleep(100)
         System.gc()
      }

      m0 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
      return m0
   }
}
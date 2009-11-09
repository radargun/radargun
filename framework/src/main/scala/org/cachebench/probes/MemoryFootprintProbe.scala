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
      for (i <- 0 to 100) {
         Thread.sleep(50)
         System.gc()
      }

      m0 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
      return m0
   }
}
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
      for (j <- 0 to 10) {
         for (i <- 0 to 100) {
            Thread.sleep(10)
            System.gc()
         }
         Thread.sleep(500)
      }

      m0 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
      return m0
   }
}
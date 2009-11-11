package org.cachebench.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;
import org.cachebench.DistStageAck;

/**
 * Distributed stage that will clear the content of the cache wrapper on each slave.
 *
 * @author Mircea.Markus@jboss.com
 */
public class ClearClusterStage extends AbstractDistStage {

   private static Log log = LogFactory.getLog(ClearClusterStage.class);

   public DistStageAck executeOnSlave() {
      DefaultDistStageAck defaultDistStageAck = newDefaultStageAck();
      CacheWrapper cacheWrapper = slaveState.getCacheWrapper();
      for (int i = 0; i < 5; i++) {
         try {
            printMemoryFootprint(true);
            cacheWrapper.empty();
            return defaultDistStageAck;
         } catch (Exception e) {
            log.warn(e);
         } finally {
           System.gc();
           printMemoryFootprint(false);
         }
      }
      defaultDistStageAck.setPayload("WARN!! Issues while clearing the cache!!!");
      return defaultDistStageAck;
   }

   private void printMemoryFootprint(boolean before) {
      Runtime run = Runtime.getRuntime();
      String memoryInfo = "Memory(KB) - free: " + kb(run.freeMemory()) + " - max:" + kb(run.maxMemory()) + "- total:" + kb(run.totalMemory());
      if (before) {
         log.info("Before executing clear, memory looks like this: " + memoryInfo);
      } else {
         log.info("After executing cleanup, memory looks like this: " + memoryInfo);
      }
   }

   private long kb(long memBytes) {
      return memBytes/1024;
   }


}

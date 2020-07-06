package org.radargun.stages;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.utils.Utils;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(internal = true, doc = "DO NOT USE DIRECTLY. This stage is automatically inserted before the beginning of scenario.")
public final class ScenarioInitStage extends AbstractDistStage {
   static final String INITIAL_FREE_MEMORY = "INITIAL_FREE_MEMORY";
   static final String INITIAL_THREADS = "INITIAL_THREADS";

   @Property(doc = "Directory where the heap dump will be produced. Contrary to scenario-cleanup, " +
      "if this directory is set, the heap dump is written always. By default the dump will not be produced.")
   private String heapDumpDir;

   @Override
   public DistStageAck executeOnWorker() {
      System.gc();
      Runtime runtime = Runtime.getRuntime();
      workerState.put(INITIAL_FREE_MEMORY, runtime.freeMemory() + runtime.maxMemory() - runtime.totalMemory());

      Thread[] activeThreads = new Thread[Thread.activeCount() * 2];
      int activeThreadCount = Thread.enumerate(activeThreads);
      Set<Thread> threads = new HashSet<>(activeThreadCount);
      for (int i = 0; i < activeThreadCount; ++i) threads.add(activeThreads[i]);
      workerState.put(INITIAL_THREADS, threads);

      if (heapDumpDir != null) {
         try {
            File heapDumpFile = new File(heapDumpDir, workerState.getConfigName() + "." + workerState.getWorkerIndex()
               + "." + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".bin");
            log.info("Dumping heap into " + heapDumpFile.getAbsolutePath());
            Utils.dumpHeap(heapDumpFile.getAbsolutePath());
            log.info("Successfully written heap dump.");
         } catch (Exception e) {
            log.error("Cannot write heap dump!", e);
         }
      }

      return successfulResponse();
   }
}

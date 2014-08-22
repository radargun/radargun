package org.radargun.stages;

import java.util.HashSet;
import java.util.Set;

import org.radargun.DistStageAck;
import org.radargun.config.Stage;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(internal = true, doc = "DO NOT USE DIRECTLY. This stage is automatically inserted before the beginning of scenario.")
public class ScenarioInitStage extends AbstractDistStage {
   static final String INITIAL_FREE_MEMORY = "INITIAL_FREE_MEMORY";
   static final String INITIAL_THREADS = "INITIAL_THREADS";

   @Override
   public DistStageAck executeOnSlave() {
      Thread.currentThread().setContextClassLoader(slaveState.getClassLoader());

      slaveState.put(INITIAL_FREE_MEMORY, Runtime.getRuntime().freeMemory());

      Thread[] activeThreads = new Thread[Thread.activeCount() * 2];
      int activeThreadCount = Thread.enumerate(activeThreads);
      Set<Thread> threads = new HashSet<>(activeThreadCount);
      for (int i = 0; i < activeThreadCount; ++i) threads.add(activeThreads[i]);
      slaveState.put(INITIAL_THREADS, threads);

      return successfulResponse();
   }
}

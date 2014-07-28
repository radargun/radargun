package org.radargun.stages.cache.stresstest;

import java.util.ArrayList;
import java.util.List;

import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stats.Statistics;

/**
 * Repeats the StressTest logic with variable amount of threads.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Repeats the StressTest logic with increasing amount of client threads.")
public class ClientStressTestStage extends StressTestStage {

   @Property(optional = false, doc = "Initial number of threads.")
   private int initThreads = 1;

   @Property(optional = false, doc = "Maximum number of threads this will be run with.")
   private int maxThreads = 10;

   @Property(doc = "Number of threads which should be added in each iteration. Default is 1.")
   private int increment = 1;

   // TODO: override loadStatistics and storeStatistics to get proper histograms

   public List<List<Statistics>> stress() {
      log.info("Client stress test with " + initThreads + " - " + maxThreads + " (increment " + increment + ")");
      int iterations = (maxThreads + increment - 1 - initThreads) / increment + 1;

      List<List<Statistics>> results = new ArrayList<List<Statistics>>();
      int iteration = 0;

      if (!startOperations()) return results;
      for (int threads = initThreads; threads <= maxThreads; threads += increment, iteration++) {
         log.info("Starting iteration " + iteration + " with " + threads);

         Completion completion;
         if (duration > 0) {
            completion = new TimeStressorCompletion(duration / iterations, requestPeriod);
         } else {
            completion = new OperationCountCompletion(numRequests / iterations, logPeriod, requestPeriod);
         }
         setCompletion(completion);

         numThreads = threads;
         try {
            executeOperations();
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
         if (isTerminated()) {
            break;
         }
         results.add(gatherResults());
      }

      finishOperations();
      return results;
   }
}

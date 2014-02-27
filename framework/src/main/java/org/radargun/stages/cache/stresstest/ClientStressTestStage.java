/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.radargun.stages.cache.stresstest;

import java.util.LinkedHashMap;
import java.util.Map;

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

   private double requestPerSec = 0;

   public Map<String, Object> stress() {
      log.info("Client stress test with " + initThreads + " - " + maxThreads + " (increment " + increment + ")");
      int iterations = (maxThreads + increment - 1 - initThreads) / increment + 1;

      Map<String, Object> results = new LinkedHashMap<String, Object>();
      int iteration = 0;

      if (!startOperations()) return results;
      for (int threads = initThreads; threads <= maxThreads; threads += increment, iteration++) {
         log.info("Starting iteration " + iteration + " with " + threads);

         Completion completion;
         if (duration > 0) {
            completion = new TimeStressorCompletion(duration / iterations);
         } else {
            completion = new OperationCountCompletion(duration / iterations, logPeriod);
         }
         setStressorCompletion(completion);

         numThreads = threads;
         try {
            executeOperations();
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
         if (isTerminated()) {
            break;
         }
         processResults(String.format("%03d", iteration), threads, results);
      }
      results.put(Statistics.REQ_PER_SEC, requestPerSec);

      finishOperations();
      return results;
   }

   protected Map<String, Object> processResults(String iteration, int threads, Map<String, Object> results) {
      Statistics stats = createStatistics();
      for (Stressor stressor : stressors) {
         stats.merge(stressor.getStats());
      }
      results.putAll(stats.getResultsMap(threads, iteration + "."));
      requestPerSec = Math.max(requestPerSec, stats.getOperationsPerSecond(true));
      return results;
   }
}

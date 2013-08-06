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
package org.radargun.stressors;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.config.Property;
import org.radargun.config.Stressor;

@Stressor(doc = "Repeats the StressTestStressor logic with increasing amount of client threads.")
public class ClientStressTestStressor extends StressTestStressor {
   private static Log log = LogFactory.getLog(ClientStressTestStressor.class);

   @Property(doc = "Initial number of threads. Default is 1.")
   private int initThreads = 1;

   @Property(doc = "Maximum number of threads. Default is 10.")
   private int maxThreads = 10;

   @Property(doc = "Number of threads by which the actual number of threads will be incremented. Default is 1.")
   private int increment = 1;
   private double requestPerSec = 0;

   public Map<String, Object> stress(CacheWrapper wrapper) {
      init(wrapper);
      log.info("Client stress test with " + initThreads + " - " + maxThreads + " (increment " + increment + ")");
      
      int iterations = (maxThreads + increment - 1 - initThreads) / increment + 1;
      
           
      Map<String, Object> results = new LinkedHashMap<String, Object>();
      int iteration = 0;

      if (!startOperations()) return results;
      for (int threads = initThreads; threads <= maxThreads; threads += increment, iteration++) {
         log.info("Starting iteration " + iteration + " with " + threads);
         
         StressorCompletion completion;
         if (getDurationMillis() > 0) {
            completion = new TimeStressorCompletion(getDurationMillis() / iterations);
         } else {
            completion = new OperationCountCompletion(new AtomicInteger(getNumRequests() / iterations));
         }
         setStressorCompletion(completion);
         
         super.setNumThreads(threads);
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
         
   @Override
   @Deprecated
   public int getNumThreads() {
      throw new UnsupportedOperationException("Set initThreads, maxThreads and increment instead");
   }
   
   @Override
   @Deprecated
   public void setNumThreads(int numberOfThreads) {
      throw new UnsupportedOperationException("Set initThreads, maxThreads and increment instead");
   }
}

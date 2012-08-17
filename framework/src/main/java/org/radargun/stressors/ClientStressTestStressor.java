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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;

public class ClientStressTestStressor extends PutGetStressor {
   private static Log log = LogFactory.getLog(ClientStressTestStressor.class);
     
   private int initThreads = 1;
   private int maxThreads = 10;
   private int increment = 1;
   private double requestPerSec = 0;
   
   public Map<String, String> stress(CacheWrapper wrapper) {
      init(wrapper);
      log.info("Client stress test with " + initThreads + " - " + maxThreads + " (increment " + increment + ")");
      
      int iterations = (maxThreads + increment - 1 - initThreads) / increment + 1;
      
           
      Map<String, String> results = new LinkedHashMap<String, String>();
      int iteration = 0;
      for (int threads = initThreads; threads <= maxThreads; threads += increment, iteration++) {
         log.info("Starting iteration " + iteration + " with " + threads);
         
         StressorCompletion completion;
         if (getDurationMillis() > 0) {
            completion = new TimeStressorCompletion(getDurationMillis() / iterations);
         } else {
            completion = new OperationCountCompletion(new AtomicInteger(getNumberOfRequests() / iterations));
         }
         setStressorCompletion(completion);
         
         List<Stressor> stressors;
         super.setNumOfThreads(threads);
         try {
            stressors = executeOperations();
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
         processResults(String.format("%03d", iteration), results, stressors);
      }
      results.put("REQ_PER_SEC", str(requestPerSec));
      return results;
   }
   
   protected Map<String, String> processResults(String iteration, Map<String, String> results, List<Stressor> stressors) {
      long duration = 0;
      long transactionDuration = 0;
      int reads = 0;
      int writes = 0;
      int failures = 0;
      long readsDurations = 0;
      long writesDurations = 0;

      for (Stressor stressor : stressors) {
         duration += stressor.totalDuration();
         readsDurations += stressor.getReadDuration();
         writesDurations += stressor.getWriteDuration();
         transactionDuration += stressor.getTransactionsDuration();

         reads += stressor.getReads();
         writes += stressor.getWrites();
         failures += stressor.getNrFailures();
      }
            
      results.put(iteration  + ".DURATION", str(duration));
      double requestPerSec = (reads + writes) / ((duration / super.getNumOfThreads()) / 1000000000.0);
      results.put(iteration  + ".REQ_PER_SEC", str(requestPerSec));
      results.put(iteration  + ".READS_PER_SEC", str(reads / ((readsDurations / super.getNumOfThreads()) / 1000000000.0)));
      results.put(iteration  + ".WRITES_PER_SEC", str(writes / ((writesDurations / super.getNumOfThreads()) / 1000000000.0)));
      results.put(iteration  + ".READ_COUNT", str(reads));
      results.put(iteration  + ".WRITE_COUNT", str(writes));
      results.put(iteration  + ".FAILURES", str(failures));
      if (isUseTransactions()) {
         double txPerSec = getTxCount() / ((transactionDuration / super.getNumOfThreads()) / 1000.0);
         results.put(iteration  + ".TX_PER_SEC", str(txPerSec));
      }
      
      this.requestPerSec = Math.max(this.requestPerSec, requestPerSec);
      return results;
   }
         
   @Override
   @Deprecated
   public int getNumOfThreads() {
      throw new UnsupportedOperationException("Set initThreads, maxThreads and increment instead");
   }
   
   @Override
   @Deprecated
   public void setNumOfThreads(int numberOfThreads) {
      throw new UnsupportedOperationException("Set initThreads, maxThreads and increment instead");
   }
   
   public int getInitThreads() {
      return initThreads;
   }

   public void setInitThreads(int initThreads) {
      this.initThreads = initThreads;
   }

   public int getMaxThreads() {
      return maxThreads;
   }

   public void setMaxThreads(int maxThreads) {
      this.maxThreads = maxThreads;
   }

   public int getIncrement() {
      return increment;
   }

   public void setIncrement(int increment) {
      this.increment = increment;
   }
}

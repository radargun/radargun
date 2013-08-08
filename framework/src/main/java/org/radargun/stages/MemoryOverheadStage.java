/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other contributors
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
package org.radargun.stages;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.state.MasterState;

@Stage(doc = "Stage which measures memory overhead of a cache. Add the following line to slave.sh: "
      + "JVM_OPTS='-server -Xmx2048M -Xms2048M -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xloggc:/tmp/gc.log'")
public class MemoryOverheadStage extends AbstractDistStage {

   private static final Random RANDOM = new Random();
   private static final String TMP_DIR = System.getProperty("java.io.tmpdir");
   private static final int MIN_MEM_DELTA = 50; //50KB

   public static final int DATA_SIZE_IN_MB = 100;
   public static final int ENTRY_SIZE = 1024; // 1024B

   public static final int NUM_ENTRIES = DATA_SIZE_IN_MB * 1024 * 1024 / ENTRY_SIZE;

   CacheWrapper cacheWrapper;

   @Property(doc = "The name of the cache bucket. Default is null.")
   String bucket = null;

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      super.processAckOnMaster(acks, masterState);
      log.info("--------------------");
      log.info("Product: " + masterState.nameOfTheCurrentBenchmark() + " has memory overhead of "
            + ((DefaultDistStageAck) acks.get(0)).getPayload() + " bytes");
      log.info("--------------------");
      return true;
   }

   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck result = newDefaultStageAck();
      cacheWrapper = slaveState.getCacheWrapper();

      int initialMemValue = repeatGC();

      final int numCycles = 5;
      int[] finalMemValues = new int[numCycles];
      for (int i = 0; i != numCycles; i++) {
         log.info("---------------------");
         log.info("Loading data...");
         long start = System.currentTimeMillis();

         try {
            loadData(NUM_ENTRIES, ENTRY_SIZE, i * (NUM_ENTRIES));
         } catch (Exception e) {
            result.setRemoteException(e);
            result.setError(true);
            return result;
         }

         long end = System.currentTimeMillis();
         log.info("Data loaded in " + (end - start) + " ms");
         int finalMemValue = repeatGC();
         log.info("Initial memory [kB]: " + initialMemValue);
         log.info("Final memory [kB]: " + finalMemValue);
         finalMemValues[i] = finalMemValue - initialMemValue;
         log.info("Memory occupied with data [kB]: " + finalMemValues[i]);
         initialMemValue = finalMemValue;
      }
      int sum = 0;
      for (int i = 1; i != numCycles; i++) { //ignore first iteration
         sum += finalMemValues[i];
      }
      log.info("---------------------");
      long occupiedMem = sum / (numCycles - 1);
      log.info("Average memory occupied with data [kB]: " + occupiedMem);
      log.info("Original entry size [B]: " + ENTRY_SIZE);
      long realEntrySize = (occupiedMem * 1024) / NUM_ENTRIES;
      log.info("Real entry size [B]: " + realEntrySize);
      log.info("Overhead per entry [B]: " + (realEntrySize - ENTRY_SIZE));
      result.setPayload(realEntrySize - ENTRY_SIZE);
      for (int i = 0; i != numCycles; i++) {
         try {
            verifyData(NUM_ENTRIES, ENTRY_SIZE, i * NUM_ENTRIES);
         } catch (Exception e) {
            result.setRemoteException(e);
            result.setError(true);
            return result;
         }
      }

      return result;
   }

   protected void loadData(int count, int entrySize, int offset) throws Exception {
      int valueSize = entrySize - 10; //10 bytes for key; key+value=entrySize
      for (int i = offset; i != (offset + count); i++) {
         cacheWrapper.put(bucket, generateStringKey(i), generateRandomBytes(valueSize));
      }
   }

   protected void verifyData(int count, int entrySize, int offset) throws Exception {
      for (int i = offset; i != (offset + count); i++) {
         byte[] b = (byte[]) cacheWrapper.get(bucket, generateStringKey(i));
         if (b == null) {
            throw new RuntimeException("Value for key " + new String(generateByteKey(i)) + " is null");
         }
         assert (b.length == (entrySize - 10));
      }
   }

   protected byte[] generateRandomBytes(int size) {
      byte[] array = new byte[size];
      nextBytes(array);
      return array;
   }

   protected byte[] generateConstBytes(int size) {
      byte[] array = new byte[size];
      for (int i = 0; i != array.length; i++) {
         array[i] = 'x';
      }
      return array;
   }

   protected void nextBytes(byte[] buf) {
      int rand = 0, count = 0;
      while (count < buf.length) {
         rand = RANDOM.nextInt();
         buf[count++] = (byte) rand;
      }
   }

   protected String generateStringKey(int index) {
      return String.format("k%09d", index); //create keys like this: k000000001, k000000002, k000000123 -> 10 bytes every time
   }

   protected byte[] generateByteKey(int index) {
      byte[] result = generateStringKey(index).getBytes();
      assert (result.length == 10);
      return result;
   }

   protected void callGC() {
      System.gc();
   }

   /**
    * Repeats garbage collection until the difference (in terms of occupied memory) between two
    * consecutive runs is small enough
    */
   protected int repeatGC() {
      int previousMemValue = Integer.MAX_VALUE;
      int newMemValue = previousMemValue;
      do {
         previousMemValue = newMemValue;
         callGC();
         newMemValue = getMemValue();
      } while (previousMemValue - newMemValue > MIN_MEM_DELTA);
      return newMemValue;
   }

   protected int getMemValue() {
      File f = new File(TMP_DIR, "gc.log");
      BufferedReader br = null;
      try {
         br = new BufferedReader(new FileReader(f));
         String line = null, lastLine = null;
         while ((line = br.readLine()) != null) {
            lastLine = line;
         }
         return latestMemValue(lastLine);
      } catch (Exception e) {
         throw new RuntimeException("Unable to parse GC log", e);
      } finally {
         try {
            if (br != null) {
               br.close();
            }
         } catch (IOException e) {
            log.info("Unable to close GC log file");
         }
      }
   }

   protected int latestMemValue(String line) {
      log.info(line);
      String s = line.substring(line.indexOf("ParOldGen"));
      s = s.substring(s.indexOf("]"));
      s = s.substring(2, s.indexOf("["));
      Pattern p = Pattern.compile("(\\d+)\\D\\->(\\d+)\\D.*");
      Matcher m = p.matcher(s);
      //returns the second number from the following pattern: 42730K->42634K(217856K), which is the final mem value
      return Integer.parseInt(m.replaceFirst("$2"));
   }

}

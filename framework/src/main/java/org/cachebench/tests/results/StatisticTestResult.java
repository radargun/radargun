package org.cachebench.tests.results;

import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math.stat.descriptive.SynchronizedDescriptiveStatistics;
import org.cachebench.reportgenerators.CsvStatisticReportGenerator;


/**
 * @author Manik Surtani (manik@surtani.org)
 * @version $Id: StatisticTestResult.java,v 1.4 2007/04/18 19:09:30 msurtani Exp $
 */
public class StatisticTestResult extends BaseTestResult {
   private final DescriptiveStatistics putData = new SynchronizedDescriptiveStatistics(),
         getData = new SynchronizedDescriptiveStatistics(),
         memdata = new SynchronizedDescriptiveStatistics();
   private int throughputTransactionsPerSecond;
   private int throughputBytesPerSecond;
   private int numMembers;
   private int numThreads;

   public DescriptiveStatistics getGetData() {
      return getData;
   }

   public DescriptiveStatistics getPutData() {
      return putData;
   }

   /**
    * This is only measured on put() operations as it has little meaning for get()s.
    */
   public int getThroughputTransactionsPerSecond() {
      return throughputTransactionsPerSecond;
   }

   public void setThroughputTransactionsPerSecond(int throughputTransactionsPerSecond) {
      this.throughputTransactionsPerSecond = throughputTransactionsPerSecond;
   }

   /**
    * This is only measured on put() operations as it has little meaning for get()s.  Note that the serialized size of
    * objects are used to calculate this, not the ACTUAL bytes transmitted, as some cache impls (JBoss Cache >= 1.4) has
    * internal marshallers that reduce object sizes to smaller than what they would be if serialized.  For the sake of
    * comparing though, one must still consider that an object of, say, 200 bytes when serialized was transmitted, even
    * if the cache compresses this to 100 bytes.
    */
   public int getThroughputBytesPerSecond() {
      return throughputBytesPerSecond;
   }

   public void setThroughputBytesPerSecond(int throughputBytesPerSecond) {
      this.throughputBytesPerSecond = throughputBytesPerSecond;
   }

   public int getNumMembers() {
      return numMembers;
   }

   public void setNumMembers(int numMembers) {
      this.numMembers = numMembers;
   }

   public int getNumThreads() {
      return numThreads;
   }

   public void setNumThreads(int numThreads) {
      this.numThreads = numThreads;
   }

   public long getFinalMemoryUsed() {
      return (long) memdata.getMean();
   }

   @Override
   public String toString() {
      return "StatisticTestResult{" +
            "putData=" + putData +
            ", getData=" + getData +
            ", throughputTransactionsPerSecond=" + throughputTransactionsPerSecond +
            ", throughputBytesPerSecond=" + throughputBytesPerSecond +
            ", numMembers=" + numMembers +
            ", numThreads=" + numThreads +
            '}';
   }

   public String getReportGeneratorClassName() {
      return CsvStatisticReportGenerator.class.getName();
   }

   public void addFinalMemoryUsed(long memoryFootprint) {
      memdata.addValue(memoryFootprint);
   }
}

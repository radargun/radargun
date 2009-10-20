package org.cachebench.reportgenerators;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;
import org.cachebench.tests.results.StatisticTestResult;
import org.cachebench.tests.results.TestResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.concurrent.TimeUnit;


/**
 * @author Manik Surtani (manik@surtani.org)
 * @version $Id: CsvStatisticReportGenerator.java,v 1.5 2007/04/18 19:09:31 msurtani Exp $
 */
public class CsvStatisticReportGenerator extends CsvBaseReportGenerator
{
   public CsvStatisticReportGenerator()
   {
      log = LogFactory.getLog(this.getClass());
   }

   /**
    * Writes out the report.
    * The method checkes whether the result is passed or failed. And based on the status would generate the report with
    * appropriate content. The method also checks whether the report has any foot notes attached to the test case. If
    * any foot note is found, then its added to the <code>footNotes</code> ArrayList for later processing.
    */
   protected void writeTestResult(TestResult results, BufferedWriter writer) throws IOException
   {
      StatisticTestResult stResults = (StatisticTestResult) results;
      log.debug("Writing the Result to the Report");
      StringBuffer buf = new StringBuffer();
      if (stResults.isTestPassed())
      {
         // This test has pased. Lets add this test results to the report.
         DescriptiveStatistics putData = stResults.getPutData();
         DescriptiveStatistics getData = stResults.getGetData();

         buf.append(stResults.getTestName());
         buf.append(",");
         buf.append(stResults.getTestTime());
         buf.append(",");
         buf.append(stResults.getTestType());
         buf.append(",");
         buf.append(stResults.getNumMembers());
         buf.append(",");
         buf.append(stResults.getNumThreads());
         buf.append(",");
         buf.append(TimeUnit.NANOSECONDS.toSeconds((long) putData.getSum()));
         buf.append(",");
         buf.append(TimeUnit.NANOSECONDS.toSeconds(((long) getData.getSum())));
         buf.append(",");
         buf.append(putData.getMean());
         buf.append(",");
         buf.append(getData.getMean());
         buf.append(",");
         // medians are the 50th percentile...
         buf.append(putData.getPercentile(50));
         buf.append(",");
         buf.append(getData.getPercentile(50));
         buf.append(",");
         buf.append(putData.getStandardDeviation());
         buf.append(",");
         buf.append(getData.getStandardDeviation());
         buf.append(",");
         buf.append(putData.getMax());
         buf.append(",");
         buf.append(getData.getMax());
         buf.append(",");
         buf.append(putData.getMin());
         buf.append(",");
         buf.append(getData.getMin());
         buf.append(",");
         buf.append(stResults.getThroughputTransactionsPerSecond());
         buf.append(",");
         buf.append(stResults.getThroughputBytesPerSecond());
         buf.append(",");
         buf.append(putData.getN());
         buf.append(",");
         buf.append(getData.getN());
      }
      else
      {
         // This test has failed. Need to add this to the report.
         buf.append(stResults.getTestName());
         buf.append(",");
         buf.append(stResults.getTestTime());
         buf.append(",");
         buf.append(stResults.getTestType());
         buf.append(",");
         buf.append(stResults.getErrorMsg());
      }

      // write details of this test to file.
      writer.write(buf.toString());
      writer.newLine();
   }

   protected void writeHeaderLine(BufferedWriter writer) throws IOException
   {
      log.debug("Write the Report Header");
      writer.write("TEST NAME, TEST DATE, TEST TYPE, NUM MEMBERS, NUM THREADS, TOTAL PUT TIME (secs), TOTAL GET TIME (secs), MEAN PUT TIME, MEAN GET TIME, MEDIAN PUT TIME, MEDIAN GET TIME, STANDARD DEVIATION PUT TIME, STANDARD DEVIATION GET TIME, MAX PUT TIME, MAX GET TIME, MIN PUT TIME, MIN GET TIME, THROUGHPUT TRANSACTIONS PER SEC, THROUGHPUT BYTES PER SEC, NUM_PUTS, NUM_GETS");
      writer.newLine();
      log.debug("Completed the Report Header");
   }


}

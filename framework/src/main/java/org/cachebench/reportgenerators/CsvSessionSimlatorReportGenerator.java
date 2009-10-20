package org.cachebench.reportgenerators;

import org.cachebench.tests.SessionSimulatorTestResult;
import org.cachebench.tests.results.TestResult;

import java.io.BufferedWriter;
import java.io.IOException;

/**
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
public class CsvSessionSimlatorReportGenerator extends CsvBaseReportGenerator
{
   protected void writeTestResult(TestResult result, BufferedWriter writer) throws IOException
   {
      SessionSimulatorTestResult ssResult = (SessionSimulatorTestResult) result;
      log.debug("Writing the Result to the Report");
      StringBuffer buf = new StringBuffer();
      if (ssResult.isTestPassed())
      {
         buf.append(ssResult.getTestName());
         buf.append(",");
         buf.append(ssResult.getTestTime());
         buf.append(",");
         buf.append(ssResult.getNoRequestPerSec());
         buf.append(",");
         buf.append(ssResult.getBytesRead());
         buf.append(",");
         buf.append(ssResult.getBytesWritten());
         buf.append(",");
         buf.append(ssResult.getDurration());
         buf.append(",");
         buf.append(ssResult.getTotalOperationCount());
         buf.append(",");
         buf.append(ssResult.getReadCount());
         buf.append(",");
         buf.append(ssResult.getWriteCount());
         if (ssResult.registeredReplicationDelays())
         {
            buf.append(",");
            buf.append(ssResult.getReplicationDelayMillis());
         }
      }
      else
      {
         // This test has failed. Need to add this to the report.
         buf.append(ssResult.getTestName());
         buf.append(",");
         buf.append(ssResult.getTestTime());
         buf.append(",");
         buf.append(ssResult.getTestType());
         buf.append(",");
         buf.append(ssResult.getErrorMsg());
      }

      // write details of this test to file.
      writer.write(buf.toString());
      writer.newLine();
   }

   protected void writeHeaderLine(BufferedWriter writer) throws IOException
   {
      log.debug("Write the Report Header");
      writer.write("TEST NAME, TEST DATE, REQ PER SEC, BYTES READ, BYTES WRITTEN, DURATION, TOTAL OPERATION COUNT, READ COUNT, WRITE COUNT, REPLICATION DELAY" );
      writer.newLine();
      log.debug("Complted the Report Header");

   }
}

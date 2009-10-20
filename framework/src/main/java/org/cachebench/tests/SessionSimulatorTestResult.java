package org.cachebench.tests;

import org.cachebench.reportgenerators.CsvSessionSimlatorReportGenerator;
import org.cachebench.tests.results.BaseTestResult;

/**
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
public class SessionSimulatorTestResult extends BaseTestResult
{

   long readCount;
   long writeCount;
   long durration;
   long bytesRead;
   long bytesWritten;

   long replicationDelayMillis = -1;


   public SessionSimulatorTestResult(long readCount, long writeCount, long durration, long bytesRead, long bytesWritten)
   {
      this.readCount = readCount;
      this.writeCount = writeCount;
      this.durration = durration;
      this.bytesRead = bytesRead;
      this.bytesWritten = bytesWritten;
   }

   public long getTotalOperationCount()
   {
      return readCount + writeCount;
   }

   public long getReadCount()
   {
      return readCount;
   }

   public long getWriteCount()
   {
      return writeCount;
   }

   public double getNoRequestPerSec()
   {
      return getTotalOperationCount() / (durration / 1000.0);
   }

   public long getBytesRead()
   {
      return bytesRead;
   }

   public long getBytesWritten()
   {
      return bytesWritten;
   }

   public long getDurration()
   {
      return durration;
   }

   public String getReportGeneratorClassName()
   {
      return CsvSessionSimlatorReportGenerator.class.getName();
   }

   public long getReplicationDelayMillis()
   {
      return replicationDelayMillis;
   }

   public void setReplicationDelayMillis(long replicationDelayMillis)
   {
      this.replicationDelayMillis = replicationDelayMillis;
   }

   public boolean registeredReplicationDelays()
   {
      return replicationDelayMillis != -1;
   }

   public void setReadCount(long readCount)
   {
      this.readCount = readCount;
   }

   public void setWriteCount(long writeCount)
   {
      this.writeCount = writeCount;
   }

   public void setDurration(long durration)
   {
      this.durration = durration;
   }

   public void setBytesRead(long bytesRead)
   {
      this.bytesRead = bytesRead;
   }

   public void setBytesWritten(long bytesWritten)
   {
      this.bytesWritten = bytesWritten;
   }
}

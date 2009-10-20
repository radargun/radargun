package org.cachebench.tests.results;

import java.util.Date;

/**
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
public class BaseTestResult implements TestResult
{

   protected String testName;
   protected String testType;
   protected Date testTime;
   protected boolean testPassed;
   protected String errorMsg;
   protected boolean skipReport;
   protected String footNote = ""; // This is utilized if special notes need to be captured for this test.


   public void setTestName(String testName)
   {
      this.testName = testName;
   }

   public void setTestType(String testType)
   {
      this.testType = testType;
   }

   public void setTestTime(Date testTime)
   {
      this.testTime = testTime;
   }

   public boolean isTestPassed()
   {
      return testPassed;
   }

   public void setTestPassed(boolean testPassed)
   {
      this.testPassed = testPassed;
   }

   public void setErrorMsg(String errorMsg)
   {
      this.errorMsg = errorMsg;
   }
   /**
    * If set to true, no reports will be generated from this test result.
    */
   public boolean isSkipReport()
   {
      return skipReport;
   }

   public void setSkipReport(boolean skipReport)
   {
      this.skipReport = skipReport;
   }


   public String getTestName()
   {
      return testName;
   }

   public String getTestType()
   {
      return testType;
   }

   public Date getTestTime()
   {
      return testTime;
   }

   public String getErrorMsg()
   {
      return errorMsg;
   }

   public String getFootNote()
   {
      return footNote;
   }

   public void setFootNote(String footNote)
   {
      this.footNote = footNote;
   }


   public String getReportGeneratorClassName()
   {
      return null;
   }
}

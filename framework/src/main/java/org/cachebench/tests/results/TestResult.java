package org.cachebench.tests.results;

import java.io.Serializable;
import java.util.Date;

/**
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
public interface TestResult extends Serializable
{

   public String getReportGeneratorClassName();

   void setTestName(String testCaseName);

   void setTestTime(Date date);

   void setTestType(String testName);

   void setTestPassed(boolean b);

   void setErrorMsg(String s);

   boolean isTestPassed();

   boolean isSkipReport();

   String getTestName();

   String getTestType();

   void setFootNote(String s);

   String getFootNote();
}

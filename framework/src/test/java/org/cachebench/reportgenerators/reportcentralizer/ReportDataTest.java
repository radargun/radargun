package org.cachebench.reportgenerators.reportcentralizer;

import org.testng.annotations.Test;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test
public class ReportDataTest {

   /*
   data_jbosscache-2.1.0_pess-repl-async.xml_2.csv
    */
   public void testParseFileName()
   {
      String filename = "data_jbosscache-2.1.0_pess-repl-async.xml_2.csv";
      ReportData reportData = new ReportData();
      reportData.processFileName(filename);
      assert reportData.getClusterSize() == 2;
      assert reportData.getConfiguration().equals("pess-repl-async.xml");
      assert reportData.getDistribution().equals("jbosscache-2.1.0");
      filename = "data_jbosscache-2.1.0_pess-repl-async.xml_22.csv";
      reportData.processFileName(filename);
      assert reportData.getClusterSize() == 22;
   }
}

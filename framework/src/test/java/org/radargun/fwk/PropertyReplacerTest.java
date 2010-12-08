package org.radargun.fwk;

import org.radargun.config.ConfigHelper;
import org.testng.annotations.Test;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test
public class PropertyReplacerTest {

   
   public void testWithDefaultNoReplacement() {
      assert "aDefaultVal".equals(ConfigHelper.checkForProps("${aDefaultVal:noSuchProp}"));
   }

   public void testWithDefaultAndReplacement(){
      System.setProperty("org.radargun.fwk.PropertyReplacerTest", "nonDefaultValue");
      String received = ConfigHelper.checkForProps("${aDefaultVal:org.radargun.fwk.PropertyReplacerTest}");
      assert "nonDefaultValue".equals(received) : received;
   }

   public void testFailureOnNonDefault() {
      try {
         ConfigHelper.checkForProps("${org.radargun.fwk.PropertyReplacerTest_other}");
         assert false : "exception expected";
      } catch (RuntimeException e) {
         //expected
      }
   }

   public void testNoDefaultAndExisting() {
      System.setProperty("org.radargun.fwk.PropertyReplacerTest", "nonDefaultValue");
      String received = ConfigHelper.checkForProps("${org.radargun.fwk.PropertyReplacerTest}");
      assert "nonDefaultValue".equals(received) : received;
   }
}

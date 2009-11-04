package org.cachebench.fwk;

import org.cachebench.fwk.config.ConfigHelper;
import org.testng.annotations.Test;

/**
 * // TODO: Mircea - Document this!
 *
 * @author Mircea.Markus@jboss.com
 */
@Test
public class PropertyReplacerTest {

   
   public void testWithDefaultNoReplacement() {
      assert "aDefaultVal".equals(ConfigHelper.checkForProps("${aDefaultVal:noSuchProp}"));
   }

   public void testWithDefaultAndReplacement(){
      System.setProperty("org.cachebench.fwk.PropertyReplacerTest", "nonDefaultValue");
      String received = ConfigHelper.checkForProps("${aDefaultVal:org.cachebench.fwk.PropertyReplacerTest}");
      assert "nonDefaultValue".equals(received) : received;
   }

   public void testFailureOnNonDefault() {
      try {
         ConfigHelper.checkForProps("${org.cachebench.fwk.PropertyReplacerTest_other}");
         assert false : "exception expected";
      } catch (RuntimeException e) {
         //expected
      }
   }

   public void testNoDefaultAndExisting() {
      System.setProperty("org.cachebench.fwk.PropertyReplacerTest", "nonDefaultValue");
      String received = ConfigHelper.checkForProps("${org.cachebench.fwk.PropertyReplacerTest}");
      assert "nonDefaultValue".equals(received) : received;
   }
}

package org.radargun.stages.test;

import java.util.Collections;

import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.AbstractMasterStage;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Adds custom result to given test")
public class AddResultStage extends AbstractMasterStage {
   @Property(doc = "Name of the test.", optional = false)
   protected String testName;

   @Property(doc = "Name of the result.", optional = false)
   protected String resultName;

   @Property(doc = "Which iteration should the result go to. Default is 0.")
   protected int iteration = 0;

   @Property(doc = "Value used as aggregation value in the test.", optional = false)
   protected String value;

   @Override
   public StageResult execute() throws Exception {
      Report.Test test = masterState.getReport().getTest(testName);
      if (test == null) {
         log.errorf("Test %s does not exist.");
         return StageResult.FAIL;
      }
      test.addResult(iteration, new Report.TestResult(resultName, Collections.EMPTY_MAP, value, false));
      return StageResult.SUCCESS;
   }
}

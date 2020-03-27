package org.radargun.reporting;

import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertFalse;

import java.util.Collection;

import org.radargun.config.MasterConfig;
import org.testng.annotations.Test;

/**
 *
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
@Test
public class AbstractReporterTest {

   public void skipOnStageFailure() throws Exception {
      // default behavior, always generate
      assertTrue(createDummyAbstractReporter(false).run(null, null, 0));
      assertTrue(createDummyAbstractReporter(false).run(null, null, 1));

      // configure to skip, return code is 0, then it will generate the report
      assertTrue(createDummyAbstractReporter(true).run(null, null, 0));

      // configure to skip, return code is a failure, then it will skip the report generation
      assertFalse(createDummyAbstractReporter(true).run(null, null, 1));
   }

   private AbstractReporter createDummyAbstractReporter(boolean skipOnStageFailures) {
      return new AbstractReporter(skipOnStageFailures) {
         @Override
         public void run(MasterConfig masterConfig, Collection<Report> reports) {
         }
      };
   }
}

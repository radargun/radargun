package org.radargun.reporting;

import java.util.Collection;

import org.radargun.config.MasterConfig;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * Control the report generation based on the stage failure
 *
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
public abstract class AbstractReporter implements Reporter {

   private static Log log = LogFactory.getLog(AbstractReporter.class);

   @Property(doc = "Set it to true if the report should be skipped when one stage failed. Default is false.")
   private boolean skipOnStageFailures;

   public AbstractReporter() {
   }

   public AbstractReporter(boolean skipOnStageFailures) {
      this.skipOnStageFailures = skipOnStageFailures;
   }

   @Override
   public boolean run(MasterConfig masterConfig, Collection<Report> reports, int returnCode) throws Exception {
      String className = this.getClass().getSimpleName();
      boolean generateReport = skipOnStageFailures ? returnCode == 0 : true;
      if (generateReport) {
         log.info("Running reporter " + className);
         run(masterConfig, reports);
         return true;
      } else {
         log.info(String.format("Skipping report %s. Return code is %d", className, returnCode));
         return false;
      }
   }

   public abstract void run(MasterConfig masterConfig, Collection<Report> reports) throws Exception;
}

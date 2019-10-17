package org.radargun.reporting;

import java.util.Collection;
import java.util.List;

import org.radargun.config.MasterConfig;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

public abstract class AbstractReporter implements Reporter {

   private static Log log = LogFactory.getLog(AbstractReporter.class);

   @Property(doc = "Which master return code should make the report run. Default is all. Comma separated.")
   private List<Integer> masterReturnCodes;

   public void run(MasterConfig masterConfig, Collection<Report> reports, int masterReturnCode) {
      String className = this.getClass().getSimpleName();
      if (masterReturnCodes == null || masterReturnCodes.isEmpty() || masterReturnCodes.contains(masterReturnCode)) {
         log.info("Running reporter " + className);
         run(masterConfig, reports);
      } else {
         log.info(String.format("Skipping report %s. Return codes are %s and the master return code is %d",
               className, masterReturnCodes, masterReturnCode));
      }
   }

   public abstract void run(MasterConfig masterConfig, Collection<Report> reports);
}

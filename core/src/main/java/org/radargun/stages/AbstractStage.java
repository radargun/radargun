package org.radargun.stages;

import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.config.StageHelper;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * Automatically describes the stage based on the annotations
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Parent class for all stages.")
public abstract class AbstractStage implements org.radargun.Stage {

   protected Log log = LogFactory.getLog(getClass());

   @Property(doc = "If true, then the benchmark stops when the stage returns an "
         + "error. If false, then the stages in the current scenario are skipped, "
         + "and the next scenario starts executing. Default is false.")
   public boolean exitOnFailure = false;

   public String getName() {
      return StageHelper.getStageName(getClass());
   }

   protected StageResult errorResult() {
      return exitOnFailure ? StageResult.EXIT : StageResult.FAIL;
   }

   @Override
   public String toString() {
      return StageHelper.toString(this);
   }
}

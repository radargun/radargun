package org.radargun.stages;

import org.radargun.config.Stage;
import org.radargun.config.StageHelper;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * Automatically describes the stage based on the annotations
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @since 12/12/12
 */
@Stage(doc = "")
public abstract class AbstractStage implements org.radargun.Stage {

   protected Log log = LogFactory.getLog(getClass());

   public String getName() {
      return StageHelper.getStageName(getClass());
   }

   @Override
   public String toString() {
      return StageHelper.toString(this);
   }
}

package org.radargun.stages;

import org.radargun.MasterStage;
import org.radargun.config.Stage;
import org.radargun.state.MasterState;

/**
 * Support class for MasterStages.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(doc = "Parent class for stages performed on master.")
public abstract class AbstractMasterStage extends AbstractStage implements MasterStage {

   protected MasterState masterState;

   public void init(MasterState masterState) {
      this.masterState = masterState;
   }
}

package org.radargun.stages;

import org.radargun.state.MasterState;
import org.radargun.MasterStage;

/**
 * Support class for MasterStages.
 *
 * @author Mircea.Markus@jboss.com
 */
public abstract class AbstractMasterStage implements MasterStage {

   protected MasterState masterState;

   public void init(MasterState masterState) {
      this.masterState = masterState;
   }

   public AbstractMasterStage clone() {
      try {
         return (AbstractMasterStage) super.clone();
      } catch (CloneNotSupportedException e) {
         throw new IllegalStateException(e);
      }
   }
}

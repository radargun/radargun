package org.radargun.stages;

import org.radargun.config.Stage;
import org.radargun.config.StageHelper;

/**
 * Automatically describes the stage based on the annotations
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @since 12/12/12
 */
@Stage(doc = "")
public abstract class AbstractStage implements org.radargun.Stage {
   @Override
   public String toString() {
      return StageHelper.toString(this);
   }

   public org.radargun.Stage clone() {
      try {
         return (org.radargun.Stage) super.clone();
      } catch (CloneNotSupportedException e) {
         throw new IllegalStateException();
      }
   }
}

package org.cachebench.fwk.stages;

import org.cachebench.fwk.Stage;

/**
 * // TODO: Mircea - Document this!
 *
 * @author
 */
public class AbstractStage implements Stage {
   private boolean skipOnFailure = true;

   public boolean skipOnFailure() {
      return skipOnFailure;
   }

   public void setSkipOnFailure(boolean skipOnFailure) {
      this.skipOnFailure = skipOnFailure;
   }
}

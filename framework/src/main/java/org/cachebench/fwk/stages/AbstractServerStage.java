package org.cachebench.fwk.stages;

import org.cachebench.fwk.state.ServerState;
import org.cachebench.fwk.ServerStage;

/**
 * // TODO: Mircea - Document this!
 *
 * @author
 */
public abstract class AbstractServerStage implements ServerStage {

   protected ServerState serverState;

   public void init(ServerState serverState) {
      this.serverState = serverState;
   }
}

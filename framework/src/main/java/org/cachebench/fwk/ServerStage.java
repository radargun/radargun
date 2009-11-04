package org.cachebench.fwk;

import org.cachebench.fwk.state.ServerState;

/**
 * // TODO: Mircea - Document this!
 *
 * @author
 */
public interface ServerStage extends Stage {

   public void init(ServerState serverState);

   public boolean execute();
}

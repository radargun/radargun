package org.cachebench.fwk.state;

import org.cachebench.fwk.ServerConfig;

/**
 * // TODO: Mircea - Document this!
 *
 * @author
 */
public class ServerState extends StateBase {
   
   private ServerConfig config;

   public ServerState(ServerConfig config) {
      this.config = config;
   }

   public ServerConfig getConfig() {
      return config;
   }
}

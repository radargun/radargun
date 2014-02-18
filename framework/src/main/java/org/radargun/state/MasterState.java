package org.radargun.state;

import org.radargun.config.MasterConfig;

/**
 * State residing on the server, passed to each stage before execution.
 *
 * @author Mircea.Markus@jboss.com
 */
public class MasterState extends StateBase {
   private MasterConfig config;

   public MasterState(MasterConfig config) {
      this.config = config;
   }

   public MasterConfig getConfig() {
      return config;
   }
}

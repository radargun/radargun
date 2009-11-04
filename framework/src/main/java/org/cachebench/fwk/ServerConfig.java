package org.cachebench.fwk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * // TODO: Mircea - Document this!
 *
 * @author
 */
public class ServerConfig {
   private int port;
   private String host;
   private int nodeCount;
   private List<Stage> stages = new ArrayList<Stage>();

   public ServerConfig(int port, String host, int nodeCount) {
      this.port = port;
      this.host = host;
      this.nodeCount = nodeCount;
   }

   public int getPort() {
      return port;
   }

   public int getNodeCount() {
      return nodeCount;
   }

   public String getHost() {
      return host;
   }

   public List<Stage> getStages() {
      return Collections.unmodifiableList(stages);
   }

   public void addStage(Stage stage) {
      stages.add(stage);
   }
}

package org.radargun;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

import org.radargun.config.Cluster;
import org.radargun.config.Configuration;
import org.radargun.config.Scenario;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class LocalSlaveConnection extends SlaveBase implements SlaveConnection {
   private static final Log log = LogFactory.getLog(LocalSlaveConnection.class);

   private Map<String, String> extras = new HashMap<String, String>();
   private BlockingQueue<Integer> stageIds = new SynchronousQueue<>();
   private BlockingQueue<DistStageAck> acks = new ArrayBlockingQueue<>(1);
   private ScenarioRunner runner;

   public LocalSlaveConnection() {
      extras.put(Properties.PROPERTY_CLUSTER_SIZE, "1");
      extras.put(Properties.PROPERTY_CLUSTER_MAX_SIZE, "1");
      extras.put(Properties.PROPERTY_SLAVE_INDEX, "0");
      extras.put(Properties.PROPERTY_GROUP_NAME, Cluster.DEFAULT_GROUP);
      extras.put(Properties.PROPERTY_GROUP_SIZE, "1");
      state.setMaxClusterSize(1);
      state.setCluster(Cluster.LOCAL);
      state.setSlaveIndex(0);
      state.setLocalAddress(InetAddress.getLoopbackAddress());
   }

   @Override
   public void establish() {
      // noop
   }

   @Override
   public void sendScenario(Scenario scenario) {
      this.scenario = scenario;
   }

   @Override
   public void sendConfiguration(Configuration configuration) {
      List<Configuration.Setup> setups = configuration.getSetups();
      if (setups.size() != 1) {
         throw new IllegalArgumentException("Cannot use multiple setups for in-VM (local) test!");
      }
      this.configuration = configuration;
      extras.put(Properties.PROPERTY_CONFIG_NAME, configuration.name);
      extras.put(Properties.PROPERTY_PLUGIN_NAME, setups.get(0).plugin);
      state.setConfigName(configuration.name);
   }

   @Override
   public void sendCluster(Cluster cluster) throws IOException {
      if (cluster != Cluster.LOCAL) {
         throw new IllegalArgumentException("Only local cluster is expected");
      }
      this.cluster = cluster;
   }

   @Override
   public List<DistStageAck> runStage(int stageId, int numSlaves) {
      if (runner == null) {
         runner = new ScenarioRunner();
         runner.start();
      }
      try {
         stageIds.put(stageId);
      } catch (InterruptedException e) {
         log.error("Interrupted when requesting stage execution", e);
         Thread.currentThread().interrupt();
         return Collections.EMPTY_LIST;
      }
      if (stageId < 0 || stageId == scenario.getStageCount() - 1) {
         try {
            runner.join();
         } catch (InterruptedException e) {
            log.error("Interrupted waiting for the runner to finish.", e);
         }
         runner = null;
         try {
            runCleanup();
         } catch (IOException e) {
            log.error("Failed executing cleanup", e);
         }
      }
      try {
         DistStageAck ack = acks.take();
         return Collections.singletonList(ack);
      } catch (InterruptedException e) {
         log.error("Waiting was interrupted", e);
         Thread.currentThread().interrupt();
         return Collections.EMPTY_LIST;
      }
   }

   @Override
   public List<Timeline> receiveTimelines(int numSlaves) throws IOException {
      return Collections.singletonList(state.getTimeline());
   }

   @Override
   public void release() {
      // noop
   }

   @Override
   protected int getNextStageId() {
      try {
         return stageIds.take();
      } catch (InterruptedException e) {
         log.error("Waiting was interrupted", e);
         Thread.currentThread().interrupt();
         return -1;
      }
   }

   @Override
   protected void sendResponse(DistStageAck response) {
      acks.add(response);
   }

   @Override
   protected Map<String, String> getCurrentExtras(Configuration configuration, Cluster cluster) {
      return extras;
   }
}

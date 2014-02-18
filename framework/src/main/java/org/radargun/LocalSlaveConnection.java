package org.radargun;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.radargun.config.Cluster;
import org.radargun.config.Configuration;
import org.radargun.config.Scenario;
import org.radargun.config.StageHelper;
import org.radargun.stages.AbstractStartStage;
import org.radargun.stages.DefaultDistStageAck;
import org.radargun.state.SlaveState;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class LocalSlaveConnection implements SlaveConnection {

   private SlaveState slaveState = new SlaveState();
   private Scenario scenario;
   private Configuration configuration;
   private Map<String, String> extras = new HashMap<String, String>();

   public LocalSlaveConnection() {
      extras.put(Properties.PROPERTY_CLUSTER_SIZE, "1");
      extras.put(Properties.PROPERTY_CLUSTER_MAX_SIZE, "1");
      extras.put(Properties.PROPERTY_SLAVE_INDEX, "0");
      extras.put(Properties.PROPERTY_GROUP_NAME, Cluster.DEFAULT_GROUP);
      extras.put(Properties.PROPERTY_GROUP_SIZE, "1");
      slaveState.setClusterSize(1);
      slaveState.setMaxClusterSize(1);
      slaveState.setSlaveIndex(0);
      slaveState.setLocalAddress(InetAddress.getLoopbackAddress());
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
      String plugin = setups.get(0).plugin;
      extras.put(Properties.PROPERTY_PLUGIN_NAME, plugin);
      slaveState.setPlugin(plugin);
      slaveState.setConfigName(configuration.name);
   }

   @Override
   public void sendCluster(Cluster cluster) throws IOException {
      // noop
   }

   @Override
   public List<DistStageAck> runStage(int stageId, int numSlaves) {
      if (stageId < 0) {
         return Collections.singletonList((DistStageAck) new DefaultDistStageAck(0, slaveState.getLocalAddress()));
      }
      Stage stage = scenario.getStage(stageId, extras);
      if (stage instanceof DistStage) {
         DistStage distStage = (DistStage) stage;
         distStage.initOnSlave(slaveState);
         if (stage instanceof AbstractStartStage) {
            List<Configuration.Setup> setups = configuration.getSetups();
            ((AbstractStartStage) stage).setup(setups.get(0).service, setups.get(0).file, setups.get(0).getProperties());
         }
         DistStageAck ack = distStage.executeOnSlave();
         return Collections.singletonList(ack);
      } else {
         throw new IllegalArgumentException("Cannot run non-distributed stage " + stageId + " = " +
               StageHelper.getStageName(stage.getClass()) + " via connection!");
      }
   }

   @Override
   public void release() {
      // noop
   }
}

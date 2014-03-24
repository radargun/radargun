package org.radargun;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.radargun.config.Cluster;
import org.radargun.config.Configuration;
import org.radargun.config.InitHelper;
import org.radargun.config.Scenario;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;
import org.radargun.stages.DefaultDistStageAck;
import org.radargun.state.SlaveState;
import org.radargun.traits.TraitHelper;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class LocalSlaveConnection implements SlaveConnection {
   private static final Log log = LogFactory.getLog(LocalSlaveConnection.class);

   private SlaveState slaveState = new SlaveState();
   private Scenario scenario;
   private Configuration configuration;
   private Map<String, String> extras = new HashMap<String, String>();
   private Object service;
   private Map<Class<?>, Object> traits;

   public LocalSlaveConnection() {
      extras.put(Properties.PROPERTY_CLUSTER_SIZE, "1");
      extras.put(Properties.PROPERTY_CLUSTER_MAX_SIZE, "1");
      extras.put(Properties.PROPERTY_SLAVE_INDEX, "0");
      extras.put(Properties.PROPERTY_GROUP_NAME, Cluster.DEFAULT_GROUP);
      extras.put(Properties.PROPERTY_GROUP_SIZE, "1");
      slaveState.setClusterSize(1);
      slaveState.setGroupSize(1);
      slaveState.setMaxClusterSize(1);
      slaveState.setSlaveIndex(0);
      slaveState.setIndexInGroup(0);
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
      String service = setups.get(0).service;
      extras.put(Properties.PROPERTY_PLUGIN_NAME, plugin);
      slaveState.setPlugin(plugin);
      slaveState.setService(service);
      slaveState.setConfigName(configuration.name);
      this.service = ServiceHelper.createService(slaveState.getClassLoadHelper().getLoader(), plugin, service, configuration.name, setups.get(0).file, setups.get(0).getProperties());
      this.traits = TraitHelper.retrieve(this.service);
      slaveState.setTraits(traits);
      slaveState.setTimeline(new Timeline(0));
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
      TraitHelper.InjectResult result = TraitHelper.inject(stage, traits);
      if (result == TraitHelper.InjectResult.FAILURE) {
         return Collections.singletonList((DistStageAck) new DefaultDistStageAck(0, InetAddress.getLoopbackAddress())
               .error("The stage missed some mandatory traits.", null));
      } else if (result == TraitHelper.InjectResult.SKIP) {
         log.info("Stage " + stage.getName() + " was skipped as it was missing some traits.");
      }

      InitHelper.init(stage);
      if (stage instanceof DistStage) {
         DistStage distStage = (DistStage) stage;
         distStage.initOnSlave(slaveState);
         long start = System.currentTimeMillis(), end;
         DistStageAck ack;
         try {
            ack = distStage.executeOnSlave();
            end = System.currentTimeMillis();
         } catch (Exception e) {
            end = System.currentTimeMillis();
            log.error("Stage execution failed", e);
            ack = new DefaultDistStageAck(0, InetAddress.getLoopbackAddress()).error("Failure", e);
         }
         slaveState.getTimeline().addEvent(Stage.STAGE, new Timeline.IntervalEvent(start, stage.getName(), end - start));
         return Collections.singletonList(ack);
      } else {
         throw new IllegalArgumentException("Cannot run non-distributed stage " + stageId + " = " + stage.getName() + " via connection!");
      }
   }

   @Override
   public List<Timeline> receiveTimelines(int numSlaves) throws IOException {
      return Collections.singletonList(slaveState.getTimeline());
   }

   @Override
   public void release() {
      // noop
   }
}

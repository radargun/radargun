package org.radargun;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.radargun.config.Cluster;
import org.radargun.config.Configuration;
import org.radargun.config.PropertyHelper;
import org.radargun.config.Scenario;
import org.radargun.config.VmArgs;
import org.radargun.reporting.Timeline;
import org.radargun.utils.ArgsHolder;
import org.radargun.utils.RestartHelper;

/**
 * Slave being coordinated by a single {@link Master} object in order to run benchmarks.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class Slave extends SlaveBase {
   private RemoteMasterConnection connection;

   public Slave(RemoteMasterConnection connection) {
      this.connection = connection;
      Runtime.getRuntime().addShutdownHook(new ShutDownHook("Slave process"));
   }

   private void run(int slaveIndex) throws Exception {
      log.debugf("Started with UUID %s", ArgsHolder.getUuid());
      InetAddress address = connection.connectToMaster(slaveIndex);
      // the provided slaveIndex is just a "recommendation"
      state.setSlaveIndex(connection.receiveSlaveIndex());
      log.info("Received slave index " + state.getSlaveIndex());
      state.setMaxClusterSize(connection.receiveSlaveCount());
      log.info("Received slave count " + state.getMaxClusterSize());
      state.setLocalAddress(address);
      while (true) {
         Object object = connection.receiveObject();
         log.trace("Received " + object);
         if (object == null) {
            log.info("Master shutdown!");
            break;
         } else if (object instanceof RemoteSlaveConnection.Restart) {
            UUID nextUuid = UUID.randomUUID();
            Configuration.Setup setup = configuration.getSetup(cluster.getGroup(slaveIndex).name);
            VmArgs vmArgs = new VmArgs();
            PropertyHelper.setPropertiesFromDefinitions(vmArgs, setup.getVmArgs(), getCurrentExtras(configuration, cluster));
            RestartHelper.spawnSlave(state.getSlaveIndex(), nextUuid, setup.plugin, vmArgs);
            connection.sendResponse(null, nextUuid);
            connection.release();
            ShutDownHook.exit(0);
         } else if (object instanceof Scenario) {
            scenario = (Scenario) object;
            ScenarioRunner runner = new ScenarioRunner();
            runner.start();
            runner.join();
            // we got ScenarioCleanup, now run the cleanup
            runCleanup();
         } else if (object instanceof Configuration) {
            configuration = (Configuration) object;
            state.setConfigName(configuration.name);
         } else if (object instanceof Cluster) {
            cluster = (Cluster) object;
         } else if (object instanceof Timeline.Request) {
            connection.sendResponse(state.getTimeline(), null);
         }
      }
      ShutDownHook.exit(0);
   }

   public static void main(String[] args) {
      ArgsHolder.init(args, ArgsHolder.ArgType.SLAVE);
      RestartHelper.init();
      if (ArgsHolder.getMasterHost() == null) {
         ArgsHolder.printUsageAndExit(ArgsHolder.ArgType.SLAVE);
      }
      Slave slave = new Slave(new RemoteMasterConnection(ArgsHolder.getMasterHost(), ArgsHolder.getMasterPort()));
      try {
         slave.run(ArgsHolder.getSlaveIndex());
      } catch (Exception e) {
         e.printStackTrace();
         ShutDownHook.exit(127);
      }
   }

   @Override
   protected int getNextStageId() throws IOException {
      return connection.receiveNextStageId();
   }

   @Override
   protected Map<String, Object> getNextMasterData() throws IOException {
      return (Map<String, Object>) connection.receiveObject();
   }

   @Override
   protected void sendResponse(DistStageAck response) throws IOException {
      connection.sendResponse(response, null);
   }

   @Override
   protected Map<String, String> getCurrentExtras(Configuration configuration, Cluster cluster) {
      Map<String, String> extras = new HashMap<String, String>();
      extras.put(Properties.PROPERTY_CONFIG_NAME, configuration.name);
      extras.put(Properties.PROPERTY_PLUGIN_NAME, state.getPlugin());
      extras.put(Properties.PROPERTY_CLUSTER_SIZE, String.valueOf(cluster.getSize()));
      extras.put(Properties.PROPERTY_CLUSTER_MAX_SIZE, String.valueOf(state.getMaxClusterSize()));
      extras.put(Properties.PROPERTY_SLAVE_INDEX, String.valueOf(state.getSlaveIndex()));
      Cluster.Group group = cluster.getGroup(state.getSlaveIndex());
      extras.put(Properties.PROPERTY_GROUP_NAME, group.name);
      extras.put(Properties.PROPERTY_GROUP_SIZE, String.valueOf(group.size));
      for (Cluster.Group g : cluster.getGroups()) {
         extras.put(Properties.PROPERTY_GROUP_PREFIX + g.name + Properties.PROPERTY_SIZE_SUFFIX, String.valueOf(group.size));
      }
      return extras;
   }
}

package org.radargun;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.radargun.config.Cluster;
import org.radargun.config.Configuration;
import org.radargun.config.Definition;
import org.radargun.config.Evaluator;
import org.radargun.config.PropertyHelper;
import org.radargun.config.Scenario;
import org.radargun.config.VmArgs;
import org.radargun.reporting.Timeline;
import org.radargun.utils.ArgsHolder;
import org.radargun.utils.RestartHelper;
import org.radargun.utils.SlaveConnectionInfo;
import org.radargun.utils.Utils;

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
            // At this point, slaveIndex == -1 so get index from state
            Configuration.Setup setup = configuration.getSetup(cluster.getGroup(state.getSlaveIndex()).name);
            VmArgs vmArgs = new VmArgs();
            PropertyHelper.setPropertiesFromDefinitions(vmArgs, setup.getVmArgs(), getCurrentExtras(configuration, cluster));
            HashMap<String, String> envs = new HashMap<>();
            for (Map.Entry<String, Definition> entry : setup.getEnvironment().entrySet()) {
               envs.put(entry.getKey(), Evaluator.parseString(entry.getValue().toString()));
            }
            RestartHelper.spawnSlave(state.getSlaveIndex(), nextUuid, setup.plugin, vmArgs, envs);
            connection.sendObject(null, nextUuid);
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
            connection.sendObject(state.getTimeline(), null);
         } else if (object instanceof SlaveConnectionInfo.Request) {
            connection.sendObject(Utils.getSlaveConnectionInfo(state.getSlaveIndex()), null);
         } else if (object instanceof RemoteSlaveConnection.SlaveAddresses) {
            state.setSlaveAddresses((RemoteSlaveConnection.SlaveAddresses) object);
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
      } catch (IOException e) {
         System.err.println("Communication with master failed");
         e.printStackTrace();
         ShutDownHook.exit(127);
      } catch (Exception e) {
         System.err.println("Unexpected error in scenario");
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
      connection.sendObject(response, null);
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
         for(int i = 0; i != g.size; i++){
            SlaveConnectionInfo ifaces = state.getSlaveAddresses(cluster, g.name, i);
            for (String s: ifaces.getInterfaceNames()) {
               extras.put(Properties.PROPERTY_GROUP_PREFIX + g.name + "." + i + "." + s, ifaces.getAddressesAsString(s, ","));
            }
         }
      }
      for (Cluster.Group g : cluster.getGroups()) {
         extras.put(Properties.PROPERTY_GROUP_PREFIX + g.name + Properties.PROPERTY_SIZE_SUFFIX, String.valueOf(group.size));
      }
      extras.put(Properties.PROPERTY_PROCESS_ID, String.valueOf(Utils.getProcessID()));
      return extras;
   }
}

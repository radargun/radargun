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
import org.radargun.utils.Utils;
import org.radargun.utils.WorkerConnectionInfo;

/**
 * Worker being coordinated by a single {@link Main} object in order to run benchmarks.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class Worker extends WorkerBase {
   private RemoteMainConnection connection;

   public Worker(RemoteMainConnection connection) {
      this.connection = connection;
      Runtime.getRuntime().addShutdownHook(new ShutDownHook("Worker process"));
   }

   private void run(int workerIndex) throws Exception {
      log.debugf("Started with UUID %s", ArgsHolder.getUuid());
      InetAddress address = connection.connectToMain(workerIndex);
      // the provided workerIndex is just a "recommendation"
      state.setWorkerIndex(connection.receiveWorkerIndex());
      log.info("Received worker index " + state.getWorkerIndex());
      state.setMaxClusterSize(connection.receiveWorkerCount());
      log.info("Received worker count " + state.getMaxClusterSize());
      state.setLocalAddress(address);
      while (true) {
         Object object = connection.receiveObject();
         log.trace("Received " + object);
         if (object == null) {
            log.info("Main shutdown!");
            break;
         } else if (object instanceof RemoteWorkerConnection.Restart) {
            UUID nextUuid = UUID.randomUUID();
            // At this point, workerIndex == -1 so get index from state
            Configuration.Setup setup = configuration.getSetup(cluster.getGroup(state.getWorkerIndex()).name);
            VmArgs vmArgs = new VmArgs();
            PropertyHelper.setPropertiesFromDefinitions(vmArgs, setup.getVmArgs(), getCurrentExtras(configuration, cluster));
            HashMap<String, String> envs = new HashMap<>();
            for (Map.Entry<String, Definition> entry : setup.getEnvironment().entrySet()) {
               envs.put(entry.getKey(), Evaluator.parseString(entry.getValue().toString()));
            }
            RestartHelper.spawnWorker(state.getWorkerIndex(), nextUuid, setup.plugin, vmArgs, envs);
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
         } else if (object instanceof WorkerConnectionInfo.Request) {
            connection.sendObject(Utils.getWorkerConnectionInfo(state.getWorkerIndex()), null);
         } else if (object instanceof RemoteWorkerConnection.WorkerAddresses) {
            state.setWorkerAddresses((RemoteWorkerConnection.WorkerAddresses) object);
         }
      }
      ShutDownHook.exit(0);
   }

   public static void main(String[] args) {
      ArgsHolder.init(args, ArgsHolder.ArgType.WORKER);
      RestartHelper.init();
      if (ArgsHolder.getMainHost() == null) {
         ArgsHolder.printUsageAndExit(ArgsHolder.ArgType.WORKER);
      }
      Worker worker = new Worker(new RemoteMainConnection(ArgsHolder.getMainHost(), ArgsHolder.getMainPort()));
      try {
         worker.run(ArgsHolder.getWorkerIndex());
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
   protected Map<String, Object> getNextMainData() throws IOException {
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
      extras.put(Properties.PROPERTY_WORKER_INDEX, String.valueOf(state.getWorkerIndex()));
      Cluster.Group group = cluster.getGroup(state.getWorkerIndex());
      extras.put(Properties.PROPERTY_GROUP_NAME, group.name);
      extras.put(Properties.PROPERTY_GROUP_SIZE, String.valueOf(group.size));
      for (Cluster.Group g : cluster.getGroups()) {
         for(int workerIndex = 0; workerIndex != g.size; workerIndex++){
            WorkerConnectionInfo ifaces = state.getWorkerAddresses(cluster, g.name, workerIndex);
            for (String ifaceName: ifaces.getInterfaceNames()) {
               int numAddresses = ifaces.getAddresses(ifaceName).size();
               if (numAddresses == 1) {
                  String propertyName =
                     String.format("%s%s.%d.%s", Properties.PROPERTY_GROUP_PREFIX, g.name,
                        workerIndex, ifaceName);
                  extras.put(propertyName, ifaces.getAddresses(ifaceName).get(0).getHostAddress());
               } else {
                  for (int addrIndex = 0; addrIndex != numAddresses; addrIndex++) {
                     //Each address of the interface has its own index as well
                     //Example: ${group.servers.0.eth2.0}
                     String propertyName =
                        String.format("%s%s.%d.%s.%d", Properties.PROPERTY_GROUP_PREFIX, g.name,
                           workerIndex, ifaceName, addrIndex);
                     extras.put(propertyName, ifaces.getAddresses(ifaceName).get(addrIndex).getHostAddress());
                  }
               }
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

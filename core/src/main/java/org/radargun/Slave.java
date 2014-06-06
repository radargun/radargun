package org.radargun;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import org.radargun.config.Cluster;
import org.radargun.config.Configuration;
import org.radargun.config.InitHelper;
import org.radargun.config.Scenario;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;
import org.radargun.state.ServiceListener;
import org.radargun.state.SlaveState;
import org.radargun.traits.TraitHelper;

/**
 * Slave being coordinated by a single {@link Master} object in order to run benchmarks.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class Slave {

   private static Log log = LogFactory.getLog(Slave.class);

   private SlaveState state = new SlaveState();
   private RemoteMasterConnection connection;

   private Scenario scenario;
   private Configuration configuration;
   private Cluster cluster;

   public Slave(RemoteMasterConnection connection) {
      this.connection = connection;
      Runtime.getRuntime().addShutdownHook(new ShutDownHook("Slave process"));
   }

   private void run(int slaveIndex) throws Exception {
      InetAddress address = connection.connectToMaster(slaveIndex);
      // the provided slaveIndex is just a "recommendation"
      state.setSlaveIndex(slaveIndex = connection.receiveSlaveIndex());
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
         } else if (object instanceof Scenario) {
            scenario = (Scenario) object;
         } else if (object instanceof Configuration) {
            configuration = (Configuration) object;
            state.setConfigName(configuration.name);
         } else if (object instanceof Cluster) {
            cluster = (Cluster) object;
            int stageId;
            Cluster.Group group = cluster.getGroup(state.getSlaveIndex());
            Configuration.Setup setup = configuration.getSetup(group.name);
            state.setCluster(cluster);
            state.setPlugin(setup.plugin);
            state.setService(setup.service);
            state.setTimeline(new Timeline(slaveIndex));
            Map<String, String> extras = getCurrentExtras(configuration, cluster);
            Object service = ServiceHelper.createService(state.getClassLoadHelper().getLoader(), setup.plugin, setup.service, configuration.name, setup.file, slaveIndex, setup.getProperties(), extras);
            Map<Class<?>, Object> traits = TraitHelper.retrieve(service);
            state.setTraits(traits);
            while ((stageId = connection.receiveNextStageId()) >= 0) {
               log.trace("Received stage ID " + stageId);
               DistStage stage = (DistStage) scenario.getStage(stageId, extras);
               TraitHelper.InjectResult result = TraitHelper.inject(stage, traits);
               DistStageAck response;
               InitHelper.init(stage);
               stage.initOnSlave(state);
               if (!stage.shouldExecute()) {
                  log.info("Stage should not be executed");
                  response = new DistStageAck(state);
               } else if (result == TraitHelper.InjectResult.SKIP) {
                  log.info("Stage was skipped because it was missing some traits");
                  response = new DistStageAck(state);
               } else if (result == TraitHelper.InjectResult.FAILURE) {
                  String message = "The stage was not executed because it missed some mandatory traits.";
                  log.error(message);
                  response = new DistStageAck(state).error(message, null);
               } else {
                  if (log.isDebugEnabled()) {
                     log.info("Starting stage " + stage);
                  } else {
                     log.info("Starting stage " + stage.getName());
                  }
                  long start = System.currentTimeMillis();
                  long end;
                  try {
                     response = stage.executeOnSlave();
                     end = System.currentTimeMillis();
                     if (response == null) {
                        response = new DistStageAck(state).error("Stage returned null response", null);
                     }
                     log.info("Finished stage " + stage.getName());
                     response.setDuration(end - start);
                  } catch (Exception e) {
                     end = System.currentTimeMillis();
                     log.error("Stage execution has failed", e);
                     response = new DistStageAck(state).error("Stage execution has failed", e);
                  }
                  state.getTimeline().addEvent(Stage.STAGE, new Timeline.IntervalEvent(start, stage.getName(), end - start));
               }
               connection.sendResponse(response);
            }
            connection.sendResponse(new DistStageAck(state));
            for (ServiceListener listener : state.getServiceListeners()) {
               listener.serviceDestroyed();
            }
         } else if (object instanceof Timeline.Request) {
            connection.sendResponse(state.getTimeline());
         }
      }
      ShutDownHook.exit(0);
   }


   public static void main(String[] args) {
      String masterHost = null;
      int masterPort = RemoteSlaveConnection.DEFAULT_PORT;
      int slaveIndex = -1;
      for (int i = 0; i < args.length - 1; i++) {
         if (args[i].equals("-master")) {
            String param = args[i + 1];
            if (param.contains(":")) {
               masterHost = param.substring(0, param.indexOf(":"));
               try {
                  masterPort = Integer.parseInt(param.substring(param.indexOf(":") + 1));
               } catch (NumberFormatException nfe) {
                  log.warn("Unable to parse port part of the master!  Failing!");
                  ShutDownHook.exit(10);
               }
            } else {
               masterHost = param;
            }
         } else if (args[i].equals("-slaveIndex")) {            
            try {
               slaveIndex = Integer.parseInt(args[i + 1]);
            } catch (NumberFormatException nfe) {
               log.warn("Unable to parse slaveIndex!  Failing!");
               ShutDownHook.exit(10);
            }
         }
      }
      if (masterHost == null) {
         printUsageAndExit();
      }
      Slave slave = new Slave(new RemoteMasterConnection(masterHost, masterPort));
      try {
         slave.run(slaveIndex);
      } catch (Exception e) {
         e.printStackTrace();
         ShutDownHook.exit(10);
      }
   }

   private static void printUsageAndExit() {
      System.out.println("Usage: start_local_slave.sh -master <host>:port");
      System.out.println("       -master: The host(and optional port) on which the master resides. If port is missing it defaults to " + RemoteSlaveConnection.DEFAULT_PORT);
      ShutDownHook.exit(1);
   }

   private Map<String, String> getCurrentExtras(Configuration configuration, Cluster cluster) {
      Map<String, String> extras = new HashMap<String, String>();
      extras.put(Properties.PROPERTY_CONFIG_NAME, configuration.name);
      extras.put(Properties.PROPERTY_PLUGIN_NAME, state.getPlugin());
      extras.put(Properties.PROPERTY_CLUSTER_SIZE, String.valueOf(cluster.getSize()));
      extras.put(Properties.PROPERTY_CLUSTER_MAX_SIZE, String.valueOf(state.getMaxClusterSize()));
      extras.put(Properties.PROPERTY_SLAVE_INDEX, String.valueOf(state.getSlaveIndex()));
      Cluster.Group group = cluster.getGroup(state.getSlaveIndex());
      extras.put(Properties.PROPERTY_GROUP_NAME, group.name);
      extras.put(Properties.PROPERTY_GROUP_SIZE, String.valueOf(group.size));
      return extras;
   }
}

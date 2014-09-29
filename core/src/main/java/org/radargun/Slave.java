package org.radargun;

import java.io.IOException;
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
import org.radargun.stages.ScenarioCleanupStage;
import org.radargun.state.SlaveState;
import org.radargun.traits.TraitHelper;
import org.radargun.utils.ArgsHolder;

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
   private int slaveIndex;

   public Slave(RemoteMasterConnection connection) {
      this.connection = connection;
      Runtime.getRuntime().addShutdownHook(new ShutDownHook("Slave process"));
   }

   private void run(int slaveIndex) throws Exception {
      InetAddress address = connection.connectToMaster(slaveIndex);
      // the provided slaveIndex is just a "recommendation"
      state.setSlaveIndex(this.slaveIndex = connection.receiveSlaveIndex());
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
            ScenarioRunner runner = new ScenarioRunner();
            runner.start();
            runner.join();
            // we got -1, now run the cleanup
            runCleanup();
         } else if (object instanceof Timeline.Request) {
            connection.sendResponse(state.getTimeline());
         }
      }
      ShutDownHook.exit(0);
   }

   private void runCleanup() throws IOException {
      DistStageAck response = null;
      try {
         Map<String, String> extras = getCurrentExtras(configuration, cluster);
         ScenarioCleanupStage stage = (ScenarioCleanupStage) scenario.getStage(scenario.getStageCount() - 1, state, extras, null);
         InitHelper.init(stage);
         stage.initOnSlave(state);
         log.info("Starting stage " + (log.isDebugEnabled() ? stage.toString() : stage.getName()));
         response = stage.executeOnSlave();
      } catch (Exception e) {
         log.error("Stage execution has failed", e);
         response = new DistStageAck(state).error("Stage execution has failed", e);
      } finally {
         if (response == null) {
            response = new DistStageAck(state).error("Stage returned null response", null);
         }
         connection.sendResponse(response);
      }
   }

   // We have to run each service in new thread in order to prevent classloader leaking
   // through thread locals
   private class ScenarioRunner extends Thread {
      private ScenarioRunner() {
         super("sc-main");
      }

      @Override
      public void run() {
         try {
            scenarioLoop();
         } catch (IOException e) {
            log.error("Communication with master failed", e);
            e.printStackTrace();
            ShutDownHook.exit(127);
         } catch (Throwable t) {
            log.error("Unexpected error in scenario", t);
            t.printStackTrace();
            ShutDownHook.exit(127);
         }
      }
   }

   private void scenarioLoop() throws IOException {
      Cluster.Group group = cluster.getGroup(state.getSlaveIndex());
      Configuration.Setup setup = configuration.getSetup(group.name);
      state.setCluster(cluster);
      state.setPlugin(setup.plugin);
      state.setService(setup.service);
      state.setTimeline(new Timeline(slaveIndex));
      Map<String, String> extras = getCurrentExtras(configuration, cluster);
      Object service = ServiceHelper.createService(state.getClassLoader(), setup.plugin, setup.service, configuration.name, setup.file, slaveIndex, setup.getProperties(), extras);
      Map<Class<?>, Object> traits = TraitHelper.retrieve(service);
      state.setTraits(traits);
      for (;;) {
         int stageId = connection.receiveNextStageId();
         log.trace("Received stage ID " + stageId);
         DistStage stage = (DistStage) scenario.getStage(stageId, state, extras, null);
         if (stage instanceof ScenarioCleanupStage) {
            // this is always the last stage and is ran in main thread (not sc-main)
            break;
         }
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
            log.info("Starting stage " + (log.isDebugEnabled() ? stage.toString() : stage.getName()));
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
   }

   public static void main(String[] args) {
      ArgsHolder.init(args, ArgsHolder.ArgType.SLAVE);
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

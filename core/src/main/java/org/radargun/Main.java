package org.radargun;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.radargun.config.Cluster;
import org.radargun.config.Configuration;
import org.radargun.config.InitHelper;
import org.radargun.config.MainConfig;
import org.radargun.config.ReporterConfiguration;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Report;
import org.radargun.reporting.Reporter;
import org.radargun.reporting.ReporterHelper;
import org.radargun.reporting.Timeline;
import org.radargun.stages.control.RepeatStage;
import org.radargun.state.MainListener;
import org.radargun.state.MainState;
import org.radargun.utils.TimeService;
import org.radargun.utils.Utils;

/**
 * This is the main that will coordinate the {@link Worker}s in order to run the benchmark.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class Main {

   private static Log log = LogFactory.getLog(Main.class);

   private final MainConfig mainConfig;
   private final MainState state;
   private final ArrayList<Report> reports = new ArrayList<>();
   private int returnCode;
   private boolean exitFlag = false;
   private RemoteWorkerConnection connection;

   public Main(MainConfig mainConfig) {
      this.mainConfig = mainConfig;
      state = new MainState(mainConfig);

      Runtime.getRuntime().addShutdownHook(new ShutDownHook("Main process"));
   }

   public MainConfig getMainConfig() {
      return mainConfig;
   }

   public void run() throws Exception {
      try {
         connection = new RemoteWorkerConnection(mainConfig.getMaxClusterSize(), mainConfig.getHost(), mainConfig.getPort());
         connection.establish();
         connection.receiveWorkerAddresses();
         state.setMaxClusterSize(mainConfig.getMaxClusterSize());
         // let's create reporters now to fail soon in case of misconfiguration
         ArrayList<Reporter> reporters = new ArrayList<>();
         for (ReporterConfiguration reporterConfiguration : mainConfig.getReporters()) {
            for (ReporterConfiguration.Report report : reporterConfiguration.getReports()) {
               reporters.add(ReporterHelper.createReporter(reporterConfiguration.type, report.getProperties()));
            }
         }

         long benchmarkStart = TimeService.currentTimeMillis();
         for (Configuration configuration : mainConfig.getConfigurations()) {
            log.info("Started benchmarking configuration '" + configuration.name + "'");
            state.setConfigName(configuration.name);
            for (MainListener listener : state.getListeners()) {
               listener.beforeConfiguration();
            }
            long configStart = TimeService.currentTimeMillis();
            for (Cluster cluster : mainConfig.getClusters()) {
               int clusterSize = cluster.getSize();
               log.info("Starting scenario on " + cluster);
               connection.sendCluster(cluster);
               connection.sendWorkerAddresses();
               connection.sendConfiguration(configuration);
               // here we should restart, therefore, we have to send it again
               connection.restartWorkers(clusterSize);
               connection.sendCluster(cluster);
               connection.sendWorkerAddresses();
               connection.sendConfiguration(configuration);
               connection.sendScenario(mainConfig.getScenario(), clusterSize);
               state.setCluster(cluster);
               state.setReport(new Report(configuration, cluster));
               for (MainListener listener : state.getListeners()) {
                  listener.beforeCluster();
               }
               long clusterStart = TimeService.currentTimeMillis();
               int stageCount = mainConfig.getScenario().getStageCount();
               // These two stages are inserted to the end of the scenario in
               // this order during parsing
               int scenarioDestroyId = stageCount - 2;
               int scenarioCleanupId = stageCount - 1;
               try {
                  try {
                     // ScenarioDestroy and ScenarioCleanup are special ones, executed always
                     int nextStageId = 0;
                     do {
                        nextStageId = executeStage(configuration, cluster, nextStageId);
                     } while (nextStageId >= 0 && nextStageId < scenarioDestroyId);
                     // run ScenarioDestroy
                  } finally {
                     executeStage(configuration, cluster, scenarioDestroyId);
                  }
               } finally {
                  // run ScenarioCleanup
                  executeStage(configuration, cluster, scenarioCleanupId);
               }
               log.info("Finished scenario on " + cluster + " in " + Utils.getMillisDurationString(TimeService.currentTimeMillis() - clusterStart));
               for (MainListener listener : state.getListeners()) {
                  listener.afterCluster();
               }
               state.getReport().addTimelines(connection.receiveTimelines(clusterSize));
               reports.add(state.getReport());
               if (exitFlag) {
                  break;
               }
            }
            log.info("Finished benchmarking configuration '" + configuration.name + "' in "
               + Utils.getMillisDurationString(TimeService.currentTimeMillis() - configStart));
            for (MainListener listener : state.getListeners()) {
               listener.afterConfiguration();
            }
            if (exitFlag) {
               log.info("Exiting whole benchmark");
               break;
            }
         }
         log.info("Executed all benchmarks in " + Utils.getMillisDurationString(TimeService.currentTimeMillis() - benchmarkStart) + ", reporting...");
         for (Reporter reporter : reporters) {
            try {
               reporter.run(mainConfig, Collections.unmodifiableList(reports), returnCode);
            } catch (Exception e) {
               log.error("Error in reporter " + reporter, e);
               returnCode = 127;
            } finally {
               InitHelper.destroy(reporter);
            }
         }
         String reportersMessage = reporters.isEmpty() ? "No reporters have been specified." : "All reporters have been executed, exiting.";
         log.info(reportersMessage);
      } catch (Throwable e) {
         log.error("Exception in Main.run: ", e);
         returnCode = 127;
      } finally {
         if (connection != null) {
            connection.release();
         }
         ShutDownHook.exit(returnCode);
      }
   }

   private int executeStage(Configuration configuration, Cluster cluster, int stageId) {
      Stage stage = mainConfig.getScenario().getStage(stageId, state, getCurrentExtras(mainConfig, configuration, cluster), state.getReport());
      InitHelper.init(stage);
      StageResult result;
      try {
         if (stage instanceof MainStage) {
            result = executeMainStage((MainStage) stage);
         } else if (stage instanceof DistStage) {
            result = executeDistStage(stageId, (DistStage) stage);
         } else {
            log.error("Stage '" + stage.getName() + "' is neither main nor distributed");
            return -1;
         }
      } finally {
         InitHelper.destroy(stage);
      }

      if (result == StageResult.SUCCESS) {
         return stageId + 1;
      } else if (result == StageResult.FAIL || result == StageResult.EXIT) {
         returnCode = mainConfig.getConfigurations().indexOf(configuration) + 1;
         if (result == StageResult.EXIT) {
            exitFlag = true;
         }
         return -1;
      } else if (result == StageResult.BREAK || result == StageResult.CONTINUE) {
         Stack<String> repeatNames = (Stack<String>) state.get(RepeatStage.REPEAT_NAMES);
         String nextLabel;
         if (repeatNames == null || repeatNames.isEmpty()) {
            log.warn("BREAK or CONTINUE used out of any repeat.");
            return -1;
         } else if (result == StageResult.BREAK) {
            nextLabel = Utils.concat(".", "repeat", repeatNames.peek(), "end");
         } else if (result == StageResult.CONTINUE) {
            nextLabel = Utils.concat(".", "repeat", repeatNames.peek(), "begin");
         } else throw new IllegalStateException();
         int nextStageId = mainConfig.getScenario().getLabel(nextLabel);
         if (nextStageId < 0) {
            log.error("No label '" + nextLabel + "' defined");
         }
         return nextStageId;
      } else {
         throw new IllegalStateException("Result does not match to any type.");
      }
   }

   private Map<String, String> getCurrentExtras(MainConfig mainConfig, Configuration configuration, Cluster cluster) {
      Map<String, String> extras = new HashMap<String, String>();
      extras.put(Properties.PROPERTY_CONFIG_NAME, configuration.name);
      extras.put(Properties.PROPERTY_CLUSTER_SIZE, String.valueOf(cluster.getSize()));
      extras.put(Properties.PROPERTY_CLUSTER_MAX_SIZE, String.valueOf(mainConfig.getMaxClusterSize()));
      // we have to define these properties because distributed stages are resolved on main as well
      extras.put(Properties.PROPERTY_PLUGIN_NAME, "__no-plugin");
      extras.put(Properties.PROPERTY_GROUP_NAME, "__main");
      extras.put(Properties.PROPERTY_GROUP_SIZE, "0");
      for (Cluster.Group group : cluster.getGroups()) {
         extras.put(Properties.PROPERTY_GROUP_PREFIX + group.name + Properties.PROPERTY_SIZE_SUFFIX, String.valueOf(group.size));
      }
      extras.put(Properties.PROPERTY_WORKER_INDEX, "-1");
      extras.put(Properties.PROPERTY_PROCESS_ID, String.valueOf(Utils.getProcessID()));
      return extras;
   }

   private StageResult executeMainStage(MainStage stage) {
      stage.init(state);
      if (log.isDebugEnabled())
         log.debug("Starting main stage " + stage.getName() + ". Details:" + stage);
      else
         log.info("Starting main stage " + stage.getName() + ".");
      long start = TimeService.currentTimeMillis(), end = start;
      try {
         StageResult result = stage.execute();
         end = TimeService.currentTimeMillis();
         if (result.isError()) {
            log.error("Execution of main stage " + stage.getName() + " failed.");
         } else {
            log.info("Finished main stage " + stage.getName());
         }
         return result;
      } catch (Exception e) {
         end = TimeService.currentTimeMillis();
         log.error("Caught exception", e);
         return StageResult.FAIL;
      } finally {
         state.getTimeline().addEvent(Stage.STAGE, new Timeline.IntervalEvent(start, stage.getName(), end - start));
      }
   }

   private StageResult executeDistStage(int stageId, DistStage stage) {
      if (log.isDebugEnabled())
         log.debug("Starting distributed stage " + stage.getName() + ". Details:" + stage);
      else
         log.info("Starting distributed stage " + stage.getName() + ".");
      int numWorkers = state.getClusterSize();
      Map<String, Object> mainData;
      try {
         stage.initOnMain(state);
         mainData = stage.createMainData();
      } catch (Exception e) {
         log.error("Failed to initialize stage", e);
         return StageResult.EXIT;
      }
      List<DistStageAck> responses = null;
      try {
         responses = connection.runStage(stageId, mainData, numWorkers);
      } catch (IOException e) {
         log.error("Error when communicating to workers");
         return StageResult.EXIT;
      }
      if (responses.size() > 1) {
         Collections.sort(responses, new Comparator<DistStageAck>() {
            @Override
            public int compare(DistStageAck o1, DistStageAck o2) {
               int thisVal = o1.getWorkerIndex();
               int anotherVal = o2.getWorkerIndex();
               return (thisVal < anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1));
            }
         });
      }
      StageResult result;
      try {
         result = stage.processAckOnMain(responses);
      } catch (Exception e) {
         log.error("Processing acks on main failed", e);
         return StageResult.EXIT;
      }
      if (result.isError()) {
         log.error("Execution of distributed stage " + stage.getName() + " failed");
      } else {
         log.info("Finished distributed stage " + stage.getName() + ".");
      }
      return result;
   }
}

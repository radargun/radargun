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
import org.radargun.config.MasterConfig;
import org.radargun.config.ReporterConfiguration;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Report;
import org.radargun.reporting.Reporter;
import org.radargun.reporting.ReporterHelper;
import org.radargun.reporting.Timeline;
import org.radargun.stages.control.RepeatStage;
import org.radargun.state.MasterState;
import org.radargun.utils.Utils;

/**
 * This is the master that will coordinate the {@link Slave}s in order to run the benchmark.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class Master {

   private static Log log = LogFactory.getLog(Master.class);

   private final MasterConfig masterConfig;
   private final MasterState state;
   private final ArrayList<Report> reports = new ArrayList<Report>();
   private int returnCode;
   private boolean exitFlag = false;

   public Master(MasterConfig masterConfig) {
      this.masterConfig = masterConfig;
      state = new MasterState(masterConfig);

      Runtime.getRuntime().addShutdownHook(new ShutDownHook("Master process"));
   }

   public void run() throws Exception {
      SlaveConnection connection = null;
      try {
         if (masterConfig.isLocal()) {
            connection = new LocalSlaveConnection();
            masterConfig.addCluster(Cluster.LOCAL);
         } else {
            connection = new RemoteSlaveConnection(masterConfig.getMaxClusterSize(), masterConfig.getHost(), masterConfig.getPort());
         }
         connection.establish();
         connection.sendScenario(masterConfig.getScenario());
         state.setMaxClusterSize(masterConfig.getMaxClusterSize());
         // let's create reporters now to fail soon in case of misconfiguration
         ArrayList<Reporter> reporters = new ArrayList<>();
         for (ReporterConfiguration reporterConfiguration : masterConfig.getReporters()) {
            for (ReporterConfiguration.Report report : reporterConfiguration.getReports()) {
               reporters.add(ReporterHelper.createReporter(reporterConfiguration.type, report.getProperties()));
            }
         }

         long benchmarkStart = System.currentTimeMillis();
         for (Configuration configuration : masterConfig.getConfigurations()) {
            log.info("Started benchmarking configuration '" + configuration.name + "'");
            state.setConfigName(configuration.name);
            connection.sendConfiguration(configuration);
            long configStart = System.currentTimeMillis();
            for (Cluster cluster : masterConfig.getClusters()) {
               log.info("Starting scenario on " + cluster);
               connection.sendCluster(cluster);
               state.setCluster(cluster);
               state.setReport(new Report(configuration, cluster));
               long clusterStart = System.currentTimeMillis();
               int stageCount = masterConfig.getScenario().getStageCount();
               int scenarioDestroyId = stageCount - 2;
               int scenarioCleanupId = stageCount - 1;
               try {
                  try {
                     // ScenarioDestroy and ScenarioCleanup are special ones, executed always
                     int nextStageId = 0;
                     do {
                        nextStageId = executeStage(connection, configuration, cluster, nextStageId);
                     } while (nextStageId >= 0 && nextStageId < scenarioDestroyId);
                     // run ScenarioDestroy
                  } finally {
                     executeStage(connection, configuration, cluster, scenarioDestroyId);
                  }
               } finally {
                  // run ScenarioCleanup
                  executeStage(connection, configuration, cluster, scenarioCleanupId);
               }
               log.info("Finished scenario on " + cluster + " in " + Utils.getMillisDurationString(System.currentTimeMillis() - clusterStart));
               state.getReport().addTimelines(connection.receiveTimelines(cluster.getSize()));
               reports.add(state.getReport());
               if (exitFlag) {
                  break;
               }
            }
            log.info("Finished benchmarking configuration '" + configuration.name + "' in "
                  + Utils.getMillisDurationString(System.currentTimeMillis() - configStart));
            if (exitFlag) {
               log.info("Exiting whole benchmark");
               break;
            }
         }
         log.info("Executed all benchmarks in " + Utils.getMillisDurationString(System.currentTimeMillis() - benchmarkStart) + ", reporting...");
         // TODO run conditions: are these really necessary?
         for (Reporter reporter : reporters) {
            try {
               log.info("Running reporter " + reporter);
               reporter.run(Collections.unmodifiableList(reports));
            } catch (Exception e) {
               log.error("Error in reporter " + reporter, e);
               returnCode = 127;
            }
         }
         String reportersMessage = reporters.isEmpty() ? "No reporters have been specified." : "All reporters have been executed, exiting.";
         log.info(reportersMessage);
      } catch (Throwable e) {
         log.error("Exception in Master.run: ", e);
         returnCode = 127;
      } finally {
         if (connection != null) {
            connection.release();
         }
         ShutDownHook.exit(returnCode);
      }
   }

   private int executeStage(SlaveConnection connection, Configuration configuration, Cluster cluster, int stageId) {
      Stage stage = masterConfig.getScenario().getStage(stageId, state, getCurrentExtras(masterConfig, configuration, cluster), state.getReport());
      InitHelper.init(stage);
      StageResult result;
      if (stage instanceof MasterStage) {
         result = executeMasterStage((MasterStage) stage);
      } else if (stage instanceof DistStage) {
         result = executeDistStage(connection, stageId, (DistStage) stage);
      } else {
         log.error("Stage '" + stage.getName() + "' is neither master nor distributed");
         return -1;
      }

      if (result == StageResult.SUCCESS) {
         return stageId + 1;
      } else if (result == StageResult.FAIL || result == StageResult.EXIT){
         returnCode = masterConfig.getConfigurations().indexOf(configuration) + 1;
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
         int nextStageId = masterConfig.getScenario().getLabel(nextLabel);
         if (nextStageId < 0) {
            log.error("No label '" + nextLabel + "' defined");
         }
         return nextStageId;
      } else {
         throw new IllegalStateException("Result does not match to any type.");
      }
   }

   private Map<String, String> getCurrentExtras(MasterConfig masterConfig, Configuration configuration, Cluster cluster) {
      Map<String, String> extras = new HashMap<String, String>();
      extras.put(Properties.PROPERTY_CONFIG_NAME, configuration.name);
      extras.put(Properties.PROPERTY_CLUSTER_SIZE, String.valueOf(cluster.getSize()));
      extras.put(Properties.PROPERTY_CLUSTER_MAX_SIZE, String.valueOf(masterConfig.getMaxClusterSize()));
      // we have to define these properties because distributed stages are resolved on master as well
      extras.put(Properties.PROPERTY_PLUGIN_NAME, "");
      extras.put(Properties.PROPERTY_GROUP_NAME, "");
      extras.put(Properties.PROPERTY_GROUP_SIZE, "");
      extras.put(Properties.PROPERTY_SLAVE_INDEX, "");
      return extras;
   }

   private StageResult executeMasterStage(MasterStage stage) {
      stage.init(state);
      if (log.isDebugEnabled())
         log.debug("Starting master stage " + stage.getName() + ". Details:" + stage);
      else
         log.info("Starting master stage " + stage.getName() + ".");
      long start = System.currentTimeMillis(), end = start;
      try {
         StageResult result = stage.execute();
         end = System.currentTimeMillis();
         if (result.isError()) {
            log.error("Execution of master stage " + stage.getName() + " failed.");
         } else {
            log.info("Finished master stage " + stage.getName());
         }
         return result;
      } catch (Exception e) {
         end = System.currentTimeMillis();
         log.error("Caught exception", e);
         return StageResult.FAIL;
      } finally {
         state.getTimeline().addEvent(Stage.STAGE, new Timeline.IntervalEvent(start, stage.getName(), end - start));
      }
   }

   private StageResult executeDistStage(SlaveConnection connection, int stageId, DistStage stage) {
      if (log.isDebugEnabled())
         log.debug("Starting distributed stage " + stage.getName() + ". Details:" + stage);
      else
         log.info("Starting distributed stage " + stage.getName() + ".");
      int numSlaves = state.getClusterSize();
      stage.initOnMaster(state);
      List<DistStageAck> responses = null;
      try {
         responses = connection.runStage(stageId, numSlaves);
      } catch (IOException e) {
         log.error("Error when communicating to slaves");
         return StageResult.EXIT;
      }
      if (responses.size() > 1) {
         Collections.sort(responses, new Comparator<DistStageAck>() {
            @Override
            public int compare(DistStageAck o1, DistStageAck o2) {
               int thisVal = o1.getSlaveIndex();
               int anotherVal = o2.getSlaveIndex();
               return (thisVal < anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1));
            }
         });
      }
      StageResult result;
      try {
         result = stage.processAckOnMaster(responses);
      } catch (Exception e) {
         log.error("Processing acks on master failed", e);
         return StageResult.EXIT;
      }
      if (result.isError()) {
         log.error("Execution of distribute stage " + stage.getName() + " failed.");
      } else {
         log.info("Finished distributed stage " + stage.getName() + ".");
      }
      return result;
   }
}

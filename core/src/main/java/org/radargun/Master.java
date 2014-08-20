package org.radargun;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

   public Master(MasterConfig masterConfig) {
      this.masterConfig = masterConfig;
      state = new MasterState(masterConfig);

      Runtime.getRuntime().addShutdownHook(new ShutDownHook("Master process"));
   }

   public void run() throws Exception {
      SlaveConnection connection = null;
      int returnCode = 0;
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
               try {
                  int stageCount = masterConfig.getScenario().getStageCount();
                  for (int stageId = 0; stageId < stageCount - 1; ++stageId) {
                     if (!executeStage(connection, configuration, cluster, stageId)) {
                        returnCode = masterConfig.getConfigurations().indexOf(configuration) + 1;
                        break;
                     }
                  }
                  // run ScenarioCleanup
                  if (!executeStage(connection, configuration, cluster, stageCount - 1)) {
                     returnCode = masterConfig.getConfigurations().indexOf(configuration) + 1;
                  }
               } finally {
                  connection.runStage(-1, cluster.getSize());
               }
               log.info("Finished scenario on " + cluster + " in " + Utils.getMillisDurationString(System.currentTimeMillis() - clusterStart));
               state.getReport().addTimelines(connection.receiveTimelines(cluster.getSize()));
               reports.add(state.getReport());
            }
            log.info("Finished benchmarking configuraion '" + configuration.name + "' in "
                  + Utils.getMillisDurationString(System.currentTimeMillis() - configStart));
         }
         log.info("Executed all benchmarks in " + Utils.getMillisDurationString(System.currentTimeMillis() - benchmarkStart) + ", reporting...");
         // TODO run conditions: are these really necessary?
         for (Reporter reporter : reporters) {
            try {
               log.info("Running reporter " + reporter);
               reporter.run(masterConfig.getScenario(), Collections.unmodifiableList(reports));
            } catch (Exception e) {
               log.error("Error in reporter " + reporter, e);
               returnCode = 127;
            }
         }
         log.info("All reporters have been executed, exiting.");
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

   private boolean executeStage(SlaveConnection connection, Configuration configuration, Cluster cluster, int stageId) throws Exception {
      Stage stage = masterConfig.getScenario().getStage(stageId, getCurrentExtras(masterConfig, configuration, cluster));
      InitHelper.init(stage);
      if (stage instanceof MasterStage) {
         if (!executeMasterStage((MasterStage) stage)) return false;
      } else if (stage instanceof DistStage) {
         if (!executeDistStage(connection, stageId, (DistStage) stage)) return false;
      } else {
         log.error("Stage '" + stage.getName() + "' is neither master nor distributed");
         return false;
      }
      return true;
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

   private boolean executeMasterStage(MasterStage stage) throws Exception {
      stage.init(state);
      if (log.isDebugEnabled())
         log.debug("Starting '" + stage.getName() + "' on master node only. Details:" + stage);
      else
         log.info("Starting '" + stage.getName() + "' on master node only.");
      long start = System.currentTimeMillis(), end = start;
      try {
         boolean successful = stage.execute();
         end = System.currentTimeMillis();
         if (successful) {
            log.trace("Master stage executed successfully " + stage.getName());
         } else {
            log.error("Exiting because issues processing current stage: " + stage.getName());
         }
         return successful;
      } catch (Exception e) {
         end = System.currentTimeMillis();
         log.error("Caught exception", e);
         return false;
      } finally {
         state.getTimeline().addEvent(Stage.STAGE, new Timeline.IntervalEvent(start, stage.getName(), end - start));
      }
   }

   private boolean executeDistStage(SlaveConnection connection, int stageId, DistStage stage) throws IOException {
      if (log.isDebugEnabled())
         log.debug("Starting distributed '" + stage.getName() + "'. Details:" + stage);
      else
         log.info("Starting distributed '" + stage.getName() + "'.");
      int numSlaves = state.getClusterSize();
      stage.initOnMaster(state);
      List<DistStageAck> responses = connection.runStage(stageId, numSlaves);
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
      if (stage.processAckOnMaster(responses)) {
         log.trace("Stage " + stage.getName() + " successfully executed.");
         return true;
      } else {
         log.warn("Execution error for current benchmark, skipping rest of the stages");
         return false;
      }
   }
}

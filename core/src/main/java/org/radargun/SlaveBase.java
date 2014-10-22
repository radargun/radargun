package org.radargun;

import java.io.IOException;
import java.util.Map;

import org.radargun.config.Cluster;
import org.radargun.config.Configuration;
import org.radargun.config.InitHelper;
import org.radargun.config.PropertyHelper;
import org.radargun.config.Scenario;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;
import org.radargun.stages.ScenarioCleanupStage;
import org.radargun.state.SlaveState;
import org.radargun.traits.TraitHelper;

/**
 * Base class for both standalone slave and slave integrated in master node (local cluster).
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class SlaveBase {
   protected final Log log = LogFactory.getLog(getClass());

   protected SlaveState state = new SlaveState();
   protected Configuration configuration;
   protected Cluster cluster;
   protected Scenario scenario;

   protected void scenarioLoop() throws IOException {
      Cluster.Group group = cluster.getGroup(state.getSlaveIndex());
      Configuration.Setup setup = configuration.getSetup(group.name);
      state.setCluster(cluster);
      state.setPlugin(setup.plugin);
      state.setService(setup.service);
      state.setTimeline(new Timeline(state.getSlaveIndex()));
      Map<String, String> extras = getCurrentExtras(configuration, cluster);
      Object service = ServiceHelper.createService(state.getClassLoader(), setup.plugin, setup.service, configuration.name, state.getSlaveIndex(), setup.getProperties(), extras);
      log.info("Service is " + service.getClass().getSimpleName() + PropertyHelper.toString(service));
      Map<Class<?>, Object> traits = TraitHelper.retrieve(service);
      state.setTraits(traits);
      for (;;) {
         int stageId = getNextStageId();
         Map<String, Object> masterData = getNextMasterData();
         for (Map.Entry<String, Object> entry : masterData.entrySet()) {
            state.put(entry.getKey(), entry.getValue());
         }
         log.trace("Received stage ID " + stageId);
         DistStage stage = (DistStage) scenario.getStage(stageId, state, extras, null);
         if (stage instanceof ScenarioCleanupStage) {
            // this is always the last stage and is ran in main thread (not sc-main)
            break;
         }
         TraitHelper.InjectResult result = null;
         DistStageAck response;
         Exception initException = null;
         try {
            result = TraitHelper.inject(stage, traits);
            InitHelper.init(stage);
            stage.initOnSlave(state);
         } catch (Exception e) {
            log.error("Stage initialization has failed", e);
            initException = e;
         }
         if (initException != null) {
            response = new DistStageAck(state).error("Stage initialization has failed", initException);
         } else if (!stage.shouldExecute()) {
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
         sendResponse(response);
      }
   }

   protected abstract int getNextStageId() throws IOException;

   protected abstract Map<String, Object> getNextMasterData() throws IOException;

   protected abstract void sendResponse(DistStageAck response) throws IOException;

   protected void runCleanup() throws IOException {
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
         sendResponse(response);
      }
   }

   protected abstract Map<String, String> getCurrentExtras(Configuration configuration, Cluster cluster);

   // We have to run each service in new thread in order to prevent classloader leaking
   // through thread locals
   protected class ScenarioRunner extends Thread {
      protected ScenarioRunner() {
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
}

package org.radargun;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import org.radargun.config.Cluster;
import org.radargun.config.Configuration;
import org.radargun.config.InitHelper;
import org.radargun.config.PropertyHelper;
import org.radargun.config.Scenario;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;
import org.radargun.stages.ScenarioCleanupStage;
import org.radargun.stages.lifecycle.ServiceStartStage;
import org.radargun.state.WorkerState;
import org.radargun.traits.TraitHelper;
import org.radargun.utils.TimeService;

/**
 * Base class for both standalone worker and worker integrated in main node (local cluster).
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class WorkerBase {
   protected final Log log = LogFactory.getLog(getClass());

   protected WorkerState state = new WorkerState();
   protected Configuration configuration;
   protected Cluster cluster;
   protected Scenario scenario;
   protected Object service;

   protected void scenarioLoop() throws IOException {
      Cluster.Group group = cluster.getGroup(state.getWorkerIndex());
      Configuration.Setup setup = configuration.getSetup(group.name);
      state.setCluster(cluster);
      state.setPlugin(setup.plugin);
      state.setService(setup.service);
      state.setTimeline(new Timeline(state.getWorkerIndex()));
      Map<String, String> extras = getCurrentExtras(configuration, cluster);
      ServiceContext context =
         new ServiceContext(group.name, setup.plugin, state.getWorkerIndex());
      ServiceHelper.setServiceContext(context);

      try {
         //eager services created before any stages are executed
         if (!setup.lazyInit) {
            createService(setup, extras);
         }

         for (;;) {
            int stageId = getNextStageId();
            log.trace("Received stage ID " + stageId);
            processMainData(getNextMainData());
            DistStage stage = (DistStage) scenario.getStage(stageId, state, extras, null);

            if (stage instanceof ScenarioCleanupStage) {
               // this is always the last stage and is ran in main thread (not sc-main)
               break;
            }
            //lazy services created during ServiceStartStage
            if (stage instanceof ServiceStartStage && setup.lazyInit) {
               stage.initOnWorker(state);
               if (stage.shouldExecute() && service == null) {
                  createService(setup, extras);
               }
            }

            TraitHelper.InjectResult result = null;
            DistStageAck response;
            Exception initException = null;
            try {
               result = TraitHelper.inject(stage, state.getTraits());
               InitHelper.init(stage);
               stage.initOnWorker(state);
            } catch (Exception e) {
               log.error("Stage '" + stage.getName() + "' initialization has failed", e);
               initException = e;
            }
            if (initException != null) {
               response = new DistStageAck(state).error("Stage '" + stage.getName() + "' initialization has failed",
                     initException);
            } else if (!stage.shouldExecute()) {
               log.info("Stage '" + stage.getName() + "' should not be executed");
               response = new DistStageAck(state);
            } else if (result == TraitHelper.InjectResult.SKIP) {
               log.info("Stage '" + stage.getName() + "' was skipped because it was missing some traits");
               response = new DistStageAck(state);
            } else if (result == TraitHelper.InjectResult.FAILURE) {
               String message = "The stage '" + stage.getName()
                     + "' was not executed because it missed some mandatory traits.";
               log.error(message);
               response = new DistStageAck(state).error(message, null);
            } else {
               String stageName = stage.getName();
               log.info("Starting stage " + (log.isDebugEnabled() ? stage.toString() : stageName));
               long start = TimeService.currentTimeMillis();
               long end;
               try {
                  response = stage.executeOnWorker();
                  end = TimeService.currentTimeMillis();
                  if (response == null) {
                     response = new DistStageAck(state).error("Stage returned null response", null);
                  }
                  log.info("Finished stage " + stageName);
                  response.setDuration(end - start);
               } catch (Exception e) {
                  end = TimeService.currentTimeMillis();
                  log.error("Stage execution has failed", e);
                  response = new DistStageAck(state).error("Stage execution has failed", e);
               } finally {
                  InitHelper.destroy(stage);
               }
               state.getTimeline().addEvent(Stage.STAGE, new Timeline.IntervalEvent(start, stageName, end - start));
            }
            sendResponse(response);
         }
      } finally {
         if (state.getTraits() != null) {
            for (Object trait : state.getTraits().values()) {
               InitHelper.destroy(trait);
            }
         }
         if (service != null) {
            InitHelper.destroy(service);
         }
      }
   }

   private void createService(Configuration.Setup setup, Map<String, String> extras) {
      service = ServiceHelper.createService(setup.service, setup.getProperties(), extras);
      log.info((setup.lazyInit ? "Lazy" : "Eager") + " Service " + service.getClass().getSimpleName() + PropertyHelper.toString(service) + " loaded.");
      state.setTraits(TraitHelper.retrieve(service));
   }

   private void processMainData(Map<String, Object> mainData) throws IOException {
      for (Map.Entry<String, Object> entry : mainData.entrySet()) {
         if (entry.getKey().equals(ServiceContext.class.getName())) {
            updateServiceContexts(entry.getValue());
         } else {
            state.put(entry.getKey(), entry.getValue());
         }
      }
   }

   private void updateServiceContexts(Object propertyMap) {
      if (propertyMap instanceof Map) {
         String excludePrefix = ServiceHelper.getContext().getPrefix();
         Map<String, Object> properties = (Map<String, Object>) propertyMap;
         //exclude own properties
         Map<String, Object> filtered = properties.entrySet()
               .stream()
               .filter(p -> ! (p == null || p.getKey().startsWith(excludePrefix)))
               .collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));
         ServiceHelper.getContext().addProperties((Map) propertyMap);
      } else {
         log.warn("Unable to update service context properties.");
      }
   }

   protected abstract int getNextStageId() throws IOException;

   protected abstract Map<String, Object> getNextMainData() throws IOException;

   protected abstract void sendResponse(DistStageAck response) throws IOException;

   protected void runCleanup() throws IOException {
      DistStageAck response = null;
      try {
         Map<String, String> extras = getCurrentExtras(configuration, cluster);
         ScenarioCleanupStage stage = (ScenarioCleanupStage) scenario.getStage(scenario.getStageCount() - 1, state,
               extras, null);
         InitHelper.init(stage);
         stage.initOnWorker(state);
         log.info("Starting stage " + (log.isDebugEnabled() ? stage.toString() : stage.getName()));
         response = stage.executeOnWorker();
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

   // In RadarGun 2.0, we had to run each service in new thread in order to prevent
   // classloader leaking through thread locals. This is not necessary anymore,
   // but we still do checks in ScenarioCleanupStage
   protected class ScenarioRunner extends Thread {
      protected ScenarioRunner() {
         super("sc-main");
      }

      @Override
      public void run() {
         try {
            scenarioLoop();
         } catch (Throwable t) {
            log.error("Unexpected error in scenario", t);
            t.printStackTrace();
            ShutDownHook.exit(127);
         }
      }
   }
}

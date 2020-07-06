package org.radargun.stages.cache.listeners.cluster;

import java.util.Collections;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.cache.generators.TimestampKeyGenerator.TimestampKey;
import org.radargun.state.WorkerState;
import org.radargun.stats.BasicOperationStats;
import org.radargun.stats.Statistics;
import org.radargun.stats.SynchronizedStatistics;
import org.radargun.traits.CacheListeners;
import org.radargun.traits.InjectTrait;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.TimeService;
import org.radargun.utils.Utils;

import static org.radargun.traits.CacheListeners.*;

/**
 * Run this stage if you want to compare performance with enabled/disabled cluster listenersTrait
 *
 * @author vchepeli@redhat.com
 * @since 2.0
 */
@Stage(doc = "Benchmark operations performance where cluster listenersTrait are enabled or disabled.")
public class RegisterListenersStage extends AbstractDistStage {

   @Property(doc = "Use sleep time to simulate some work on listener. Default is -1(do not sleep) ms.", converter = TimeConverter.class)
   protected long sleepTime = -1;

   @Property(doc = "Before stress stage, cluster listeners would be enabled. This is flag to turn them on. Default is false.")
   protected boolean registerListeners = false;

   @Property(doc = "Before stress stage, cluster listeners would be disabled. This is flag to turn them off. Default is false.")
   protected boolean unregisterListeners = false;

   @Property(doc = "Name of the test as used for reporting. Default is 'Test'.")
   protected String testName = "Listeners";

   @Property(doc = "Setup if cache listener is synchronous/asynchronous. Default is true")
   private boolean sync = true;

   @Property(doc = "Allows to reset statistics at the begining of the stage. Default is false.")
   private boolean resetStats = false;

   @InjectTrait // with infinispan70 plugin
   private CacheListeners listenersTrait;

   private SynchronizedStatistics statistics;

   @Override
   public DistStageAck executeOnWorker() {
      String statsKey = getClass().getName() + ".Stats";

      statistics = (SynchronizedStatistics) workerState.get(statsKey);
      if (statistics == null) {
         statistics = new SynchronizedStatistics(new BasicOperationStats());
         workerState.put(statsKey, statistics);
      } else if (resetStats) {
         statistics.reset();
      }

      if (registerListeners) {
         initListenersOnWorker(workerState);
         registerListeners();
      }

      if (unregisterListeners) {
         unregisterListeners();
      }

      return new ListenersAck(workerState, statistics.snapshot(true));
   }

   @Override
   public StageResult processAckOnMain(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMain(acks);
      if (result.isError()) return result;

      Report.Test test = createTest(testName, null);
      if (test != null) {
         int testIteration = test.getIterations().size();

         for (ListenersAck ack : instancesOf(acks, ListenersAck.class)) {
            if (ack.stats != null)
               test.addStatistics(testIteration, ack.getWorkerIndex(), Collections.singletonList(ack.stats));
         }
      }
      return StageResult.SUCCESS;
   }

   protected Report.Test createTest(String testName, String iterationName) {
      if (testName == null || testName.isEmpty()) {
         log.warn("No test name - results are not recorded");
         return null;
      } else {
         Report report = mainState.getReport();
         return report.createTest(testName, iterationName, true);
      }
   }

   private void initListenersOnWorker(WorkerState workerState) {
      CreatedListener createdListener = new CreatedListener() {
         @Override
         public void created(Object key, Object value) {
            if (sleepTime > 0) Utils.sleep(sleepTime);
            if (key instanceof TimestampKey) {
               statistics.message().times(((TimestampKey) key).getTimestamp(), TimeService.currentTimeMillis()).record(CREATED);
            }
            log.trace("Created " + key + " -> " + value);
         }
      };
      workerState.put(CREATED.name, createdListener);

      EvictedListener evictedListener = new EvictedListener() {
         @Override
         public void evicted(Object key, Object value) {
            if (sleepTime > 0) Utils.sleep(sleepTime);
            if (key instanceof TimestampKey) {
               statistics.message().times(((TimestampKey) key).getTimestamp(), TimeService.currentTimeMillis()).record(EVICTED);
            }
            log.trace("Evicted " + key + " -> " + value);
         }
      };
      workerState.put(EVICTED.name, evictedListener);

      RemovedListener removedListener = new RemovedListener() {
         @Override
         public void removed(Object key, Object value) {
            if (sleepTime > 0) Utils.sleep(sleepTime);
            if (key instanceof TimestampKey) {
               statistics.message().times(((TimestampKey) key).getTimestamp(), TimeService.currentTimeMillis()).record(REMOVED);
            }
            log.trace("Removed " + key + " -> " + value);
         }
      };
      workerState.put(REMOVED.name, removedListener);

      UpdatedListener updatedListener = new UpdatedListener() {
         @Override
         public void updated(Object key, Object value) {
            if (sleepTime > 0) Utils.sleep(sleepTime);
            if (key instanceof TimestampKey) {
               statistics.message().times(((TimestampKey) key).getTimestamp(), TimeService.currentTimeMillis()).record(UPDATED);
            }
            log.trace("Updated " + key + " -> " + value);
         }
      };
      workerState.put(UPDATED.name, updatedListener);

      ExpiredListener expiredListener = new ExpiredListener() {
         @Override
         public void expired(Object key, Object value) {
            if (sleepTime > 0) Utils.sleep(sleepTime);
            if (key instanceof TimestampKey) {
               statistics.message().times(((TimestampKey) key).getTimestamp(), TimeService.currentTimeMillis()).record(EXPIRED);
            }
            log.trace("Expired " + key + " -> " + value);
         }
      };
      workerState.put(EXPIRED.name, expiredListener);
   }

   public void registerListeners() {
      CreatedListener createdListener = (CreatedListener) workerState.get(CREATED.name);
      if (createdListener != null && isSupported(Type.CREATED)) {
         listenersTrait.addCreatedListener(null, createdListener, sync);
      }
      EvictedListener evictedListener = (EvictedListener) workerState.get(EVICTED.name);
      if (evictedListener != null && isSupported(Type.EVICTED)) {
         listenersTrait.addEvictedListener(null, evictedListener, sync);
      }
      RemovedListener removedListener = (RemovedListener) workerState.get(REMOVED.name);
      if (removedListener != null && isSupported(Type.REMOVED)) {
         listenersTrait.addRemovedListener(null, removedListener, sync);
      }
      UpdatedListener updatedListener = (UpdatedListener) workerState.get(UPDATED.name);
      if (updatedListener != null && isSupported(Type.UPDATED)) {
         listenersTrait.addUpdatedListener(null, updatedListener, sync);
      }
      ExpiredListener expiredListener = (ExpiredListener) workerState.get(EXPIRED.name);
      if (expiredListener != null && isSupported(Type.EXPIRED)) {
         listenersTrait.addExpiredListener(null, expiredListener, sync);
      }
   }

   public void unregisterListeners() {
      CreatedListener createdListener = (CreatedListener) workerState.get(CREATED.name);
      if (createdListener != null && isSupported(Type.CREATED)) {
         listenersTrait.removeCreatedListener(null, createdListener, sync);
      }
      workerState.remove(CREATED.name);
      EvictedListener evictedListener = (EvictedListener) workerState.get(EVICTED.name);
      if (evictedListener != null && isSupported(Type.EVICTED)) {
         listenersTrait.removeEvictedListener(null, evictedListener, sync);
      }
      workerState.remove(EVICTED.name);
      RemovedListener removedListener = (RemovedListener) workerState.get(REMOVED.name);
      if (removedListener != null && isSupported(Type.REMOVED)) {
         listenersTrait.removeRemovedListener(null, removedListener, sync);
      }
      workerState.remove(REMOVED.name);
      UpdatedListener updatedListener = (UpdatedListener) workerState.get(UPDATED.name);
      if (updatedListener != null && isSupported(Type.UPDATED)) {
         listenersTrait.removeUpdatedListener(null, updatedListener, sync);
      }
      workerState.remove(UPDATED.name);
      ExpiredListener expiredListener = (ExpiredListener) workerState.get(EXPIRED.name);
      if (expiredListener != null && isSupported(Type.EXPIRED)) {
         listenersTrait.removeExpiredListener(null, expiredListener, sync);
      }
      workerState.remove(EXPIRED.name);
   }

   private boolean isSupported(Type type) {
      if (listenersTrait == null) {
         throw new IllegalArgumentException("Service does not support cache listeners");
      }
      return listenersTrait.getSupportedListeners().contains(type);
   }

   private static class ListenersAck extends DistStageAck {

      public final Statistics stats;

      public ListenersAck(WorkerState workerState, Statistics stats) {
         super(workerState);
         this.stats = stats;
      }
   }

}
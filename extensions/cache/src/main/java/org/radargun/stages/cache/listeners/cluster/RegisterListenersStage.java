package org.radargun.stages.cache.listeners.cluster;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.cache.generators.TimestampKeyGenerator.TimestampKey;
import org.radargun.state.SlaveState;
import org.radargun.stats.DefaultOperationStats;
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
   public DistStageAck executeOnSlave() {
      String statsKey = getClass().getName() + ".Stats";

      statistics = (SynchronizedStatistics) slaveState.get(statsKey);
      if (statistics == null) {
         statistics = new SynchronizedStatistics(new DefaultOperationStats());
         slaveState.put(statsKey, statistics);
      } else if (resetStats) {
         statistics.reset();
      }

      if (registerListeners) {
         initListenersOnSlave(slaveState);
         registerListeners();
      }

      if (unregisterListeners) {
         unregisterListeners();
      }

      return new ListenersAck(slaveState, statistics.snapshot(true));
   }

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMaster(acks);
      if (result.isError()) return result;

      Report.Test test = createTest(testName, null);
      if (test != null) {
         int testIteration = test.getIterations().size();

         for (ListenersAck ack : instancesOf(acks, ListenersAck.class)) {
            if (ack.stats != null)
               test.addStatistics(testIteration, ack.getSlaveIndex(), Collections.singletonList(ack.stats));
         }
      }
      return StageResult.SUCCESS;
   }

   protected Report.Test createTest(String testName, String iterationName) {
      if (testName == null || testName.isEmpty()) {
         log.warn("No test name - results are not recorded");
         return null;
      } else {
         Report report = masterState.getReport();
         return report.createTest(testName, iterationName, true);
      }
   }

   private void initListenersOnSlave(SlaveState slaveState) {
      CreatedListener createdListener = new CreatedListener() {
         @Override
         public void created(Object key, Object value) {
            if (sleepTime > 0) Utils.sleep(sleepTime);
            statistics.registerRequest(getResponseTime(key), CREATED);
            log.trace("Created " + key + " -> " + value);
         }
      };
      slaveState.put(CREATED.name, createdListener);

      EvictedListener evictedListener = new EvictedListener() {
         @Override
         public void evicted(Object key, Object value) {
            if (sleepTime > 0) Utils.sleep(sleepTime);
            statistics.registerRequest(getResponseTime(key), EVICTED);
            log.trace("Evicted " + key + " -> " + value);
         }
      };
      slaveState.put(EVICTED.name, evictedListener);

      RemovedListener removedListener = new RemovedListener() {
         @Override
         public void removed(Object key, Object value) {
            if (sleepTime > 0) Utils.sleep(sleepTime);
            statistics.registerRequest(getResponseTime(key), REMOVED);
            log.trace("Removed " + key + " -> " + value);
         }
      };
      slaveState.put(REMOVED.name, removedListener);

      UpdatedListener updatedListener = new UpdatedListener() {
         @Override
         public void updated(Object key, Object value) {
            if (sleepTime > 0) Utils.sleep(sleepTime);
            statistics.registerRequest(getResponseTime(key), UPDATED);
            log.trace("Updated " + key + " -> " + value);
         }
      };
      slaveState.put(UPDATED.name, updatedListener);

      ExpiredListener expiredListener = new ExpiredListener() {
         @Override
         public void expired(Object key, Object value) {
            if (sleepTime > 0) Utils.sleep(sleepTime);
            statistics.registerRequest(getResponseTime(key), EXPIRED);
            log.trace("Expired " + key + " -> " + value);
         }
      };
      slaveState.put(EXPIRED.name, expiredListener);
   }

   private long getResponseTime(Object key) {
      if (key instanceof TimestampKey) {
         return (TimeUnit.NANOSECONDS.convert(TimeService.currentTimeMillis() - ((TimestampKey) key).getTimestamp(), TimeUnit.MILLISECONDS));
      }
      return 0; //latency of event arrival is not measured
   }

   public void registerListeners() {
      CreatedListener createdListener = (CreatedListener) slaveState.get(CREATED.name);
      if (createdListener != null && isSupported(Type.CREATED)) {
         listenersTrait.addCreatedListener(null, createdListener, sync);
      }
      EvictedListener evictedListener = (EvictedListener) slaveState.get(EVICTED.name);
      if (evictedListener != null && isSupported(Type.EVICTED)) {
         listenersTrait.addEvictedListener(null, evictedListener, sync);
      }
      RemovedListener removedListener = (RemovedListener) slaveState.get(REMOVED.name);
      if (removedListener != null && isSupported(Type.REMOVED)) {
         listenersTrait.addRemovedListener(null, removedListener, sync);
      }
      UpdatedListener updatedListener = (UpdatedListener) slaveState.get(UPDATED.name);
      if (updatedListener != null && isSupported(Type.UPDATED)) {
         listenersTrait.addUpdatedListener(null, updatedListener, sync);
      }
      ExpiredListener expiredListener = (ExpiredListener) slaveState.get(EXPIRED.name);
      if (expiredListener != null && isSupported(Type.EXPIRED)) {
         listenersTrait.addExpiredListener(null, expiredListener, sync);
      }
   }

   public void unregisterListeners() {
      CreatedListener createdListener = (CreatedListener) slaveState.get(CREATED.name);
      if (createdListener != null && isSupported(Type.CREATED)) {
         listenersTrait.removeCreatedListener(null, createdListener, sync);
      }
      EvictedListener evictedListener = (EvictedListener) slaveState.get(EVICTED.name);
      if (evictedListener != null && isSupported(Type.EVICTED)) {
         listenersTrait.removeEvictedListener(null, evictedListener, sync);
      }
      RemovedListener removedListener = (RemovedListener) slaveState.get(REMOVED.name);
      if (removedListener != null && isSupported(Type.REMOVED)) {
         listenersTrait.removeRemovedListener(null, removedListener, sync);
      }
      UpdatedListener updatedListener = (UpdatedListener) slaveState.get(UPDATED.name);
      if (updatedListener != null && isSupported(Type.UPDATED)) {
         listenersTrait.removeUpdatedListener(null, updatedListener, sync);
      }
      ExpiredListener expiredListener = (ExpiredListener) slaveState.get(EXPIRED.name);
      if (expiredListener != null && isSupported(Type.EXPIRED)) {
         listenersTrait.removeExpiredListener(null, expiredListener, sync);
      }
   }

   private boolean isSupported(Type type) {
      if (listenersTrait == null) {
         throw new IllegalArgumentException("Service does not support cache listeners");
      }
      return listenersTrait.getSupportedListeners().contains(type);
   }

   private static class ListenersAck extends DistStageAck {

      public final Statistics stats;

      public ListenersAck(SlaveState slaveState, Statistics stats) {
         super(slaveState);
         this.stats = stats;
      }
   }

}
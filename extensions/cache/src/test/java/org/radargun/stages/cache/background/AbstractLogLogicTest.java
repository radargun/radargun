package org.radargun.stages.cache.background;

import org.mockito.Mockito;
import org.radargun.config.Cluster;
import org.radargun.logging.Log;
import org.radargun.stages.helpers.Range;
import org.radargun.state.SlaveState;
import org.radargun.traits.BasicOperations;
import org.radargun.util.CacheTestUtils;
import org.radargun.util.CacheTraitRepository;
import org.radargun.util.ReflectionUtils;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * @author Matej Cimbora
 */
@Test
public class AbstractLogLogicTest {

   public void testInit() throws NoSuchFieldException, IllegalAccessException {
      BasicOperations.Cache cache = new CacheTraitRepository.BasicOperationsCache<>();
      AbstractLogLogic logic = createLogic(new GeneralConfiguration(), new LegacyLogicConfiguration(), new LogLogicConfiguration(), cache);

      cache.put(LogChecker.lastOperationKey(logic.stressor.id), new LogChecker.LastOperation(10, 123));

      logic.init();

      assertEquals(logic.operationId, 11);
      assertNotNull(logic.keySelectorRandom);
   }

   public void testInvoke() throws NoSuchFieldException, IllegalAccessException, InterruptedException {
      BasicOperations.Cache cache = new CacheTraitRepository.BasicOperationsCache<>();
      AbstractLogLogic logic = createLogic(new GeneralConfiguration(), new LegacyLogicConfiguration(), new LogLogicConfiguration(), cache);
      logic.keySelectorRandom = mock(Random.class);

      doReturn(true).when(logic).invokeOn(anyLong());
      assertEquals(logic.operationId, 0);

      logic.invoke();
      assertEquals(logic.operationId, 1);

      doReturn(true).when(logic).invokeOn(anyLong());
      logic.invoke();
      assertEquals(logic.operationId, 2);
      assertNull(getFailureManager(logic).getError(true));
   }

   public void testInvokeOperationFailures() throws NoSuchFieldException, IllegalAccessException, InterruptedException {
      BasicOperations.Cache cache = new CacheTraitRepository.BasicOperationsCache<>();
      AbstractLogLogic logic = createLogic(new GeneralConfiguration(), new LegacyLogicConfiguration(), new LogLogicConfiguration(), cache);
      logic.keySelectorRandom = mock(Random.class);

      doReturn(false).doReturn(false).doReturn(true).when(logic).invokeOn(anyLong());
      assertEquals(logic.operationId, 0);

      logic.invoke();
      assertEquals(logic.operationId, 1);
      assertNull(getFailureManager(logic).getError(true));
   }

   public void testInvokeTxFailures() throws NoSuchFieldException, IllegalAccessException, InterruptedException {
      BasicOperations.Cache cache = new CacheTraitRepository.BasicOperationsCache<>();
      LogLogicConfiguration llc = new LogLogicConfiguration();
      llc.maxTransactionAttempts = 2;
      AbstractLogLogic logic = createLogic(new GeneralConfiguration(), new LegacyLogicConfiguration(), llc, cache);
      logic.keySelectorRandom = mock(Random.class);
      ReflectionUtils.setClassProperty(AbstractLogLogic.class, logic, "txRolledBack", true);

      doReturn(true).when(logic).invokeOn(anyLong());

      logic.invoke();
      assertEquals(getFailureManager(logic).getFailedTransactionAttempts(), 0);

      ReflectionUtils.setClassProperty(AbstractLogLogic.class, logic, "txRolledBack", true);
      logic.invoke();
      assertEquals(getFailureManager(logic).getFailedTransactionAttempts(), 0);

      ReflectionUtils.setClassProperty(AbstractLogLogic.class, logic, "txRolledBack", true);
      logic.invoke();
      assertEquals(getFailureManager(logic).getFailedTransactionAttempts(), 1);
   }

   public void testWriteStressorLastOperation() throws NoSuchFieldException, IllegalAccessException {
      BasicOperations.Cache cache = new CacheTraitRepository.BasicOperationsCache<>();
      AbstractLogLogic logic = createLogic(new GeneralConfiguration(), new LegacyLogicConfiguration(), new LogLogicConfiguration(), cache);
      logic.keySelectorRandom = mock(Random.class);
      ReflectionUtils.setClassProperty(Random.class, logic.keySelectorRandom, "seed", new AtomicLong(123));

      assertNull(cache.get(LogChecker.lastOperationKey(logic.stressor.id)));
      assertEquals(logic.operationId, 0);

      logic.writeStressorLastOperation();

      LogChecker.LastOperation lastOperation = (LogChecker.LastOperation) cache.get(LogChecker.lastOperationKey(logic.stressor.id));
      assertNotNull(lastOperation);
      assertEquals(lastOperation.getOperationId(), 0);
   }

   public void testAfterCommitFailures() throws Exception {
      BasicOperations.Cache cache = new CacheTraitRepository.BasicOperationsCache<>();
      LogLogicConfiguration llc = new LogLogicConfiguration();
      llc.maxDelayedRemoveAttempts = 2;
      AbstractLogLogic logic = createLogic(new GeneralConfiguration(), new LegacyLogicConfiguration(), llc, cache);
      Map<Long, AbstractLogLogic.DelayedRemove> delayedRemoves = new HashMap<>(1);
      delayedRemoves.put(0l, logic.new DelayedRemove(0, new PrivateLogValue(0, 1)));
      logic.delayedRemoves = delayedRemoves;

      doNothing().when(logic).startTransaction();
      Mockito.doThrow(CacheTestUtils.TestException.class).when(logic).checkedRemoveValue(anyLong(), anyObject());

      logic.afterCommit();

      assertEquals(getFailureManager(logic).getDelayedRemovesErrors(), 1);
   }

   public void testGetCheckedOperationPresentInCache() throws NoSuchFieldException, IllegalAccessException, StressorException, BreakTxRequest {
      BasicOperations.Cache cache = new CacheTraitRepository.BasicOperationsCache<>();
      AbstractLogLogic logic = createLogic(new GeneralConfiguration(), new LegacyLogicConfiguration(), new LogLogicConfiguration(), cache);

      Cluster cluster = new Cluster();
      cluster.addGroup("group", 2);
      logic.manager.getSlaveState().setCluster(cluster);

      cache.put(LogChecker.checkerKey(0, logic.stressor.id), new LogChecker.LastOperation(10, 123));
      cache.put(LogChecker.checkerKey(1, logic.stressor.id), new LogChecker.LastOperation(15, 123));

      assertEquals(logic.getCheckedOperation(logic.stressor.id, 20), 10);
   }

   public void testGetCheckedOperationNotPresentInCache() throws NoSuchFieldException, IllegalAccessException, StressorException, BreakTxRequest {
      BasicOperations.Cache cache = new CacheTraitRepository.BasicOperationsCache<>();
      AbstractLogLogic logic = createLogic(new GeneralConfiguration(), new LegacyLogicConfiguration(), new LogLogicConfiguration(), cache);

      Cluster cluster = new Cluster();
      cluster.addGroup("group", 2);
      logic.manager.getSlaveState().setCluster(cluster);

      assertEquals(logic.getCheckedOperation(logic.stressor.id, 20), Long.MIN_VALUE);
   }

   public void testGetCheckedOperationIgnoreDeadCheckers() throws NoSuchFieldException, IllegalAccessException, StressorException, BreakTxRequest {
      BasicOperations.Cache cache = new CacheTraitRepository.BasicOperationsCache<>();
      LogLogicConfiguration llc = new LogLogicConfiguration();
      llc.ignoreDeadCheckers = true;
      GeneralConfiguration gc = new GeneralConfiguration();
      gc.transactionSize = 10;
      AbstractLogLogic logic = createLogic(gc, new LegacyLogicConfiguration(), llc, cache);

      Cluster cluster = new Cluster();
      cluster.addGroup("group", 2);
      logic.manager.getSlaveState().setCluster(cluster);

      cache.put(LogChecker.checkerKey(0, logic.stressor.id), new LogChecker.LastOperation(10, 123));
      cache.put(LogChecker.checkerKey(1, logic.stressor.id), new LogChecker.LastOperation(15, 123));

      BreakTxRequest expectedException = null;
      try {
         logic.getCheckedOperation(logic.stressor.id, 20);
      } catch (BreakTxRequest ex) {
         expectedException = ex;
      }
      assertEquals(cache.get(LogChecker.ignoredKey(0, logic.stressor.id)), 20l);
      assertNotNull(expectedException);
   }

   public void testGetCheckedOperationIgnoreDeadCheckersInitial1() throws NoSuchFieldException, IllegalAccessException, StressorException, BreakTxRequest {
      BasicOperations.Cache cache = new CacheTraitRepository.BasicOperationsCache<>();
      LogLogicConfiguration llc = new LogLogicConfiguration();
      llc.ignoreDeadCheckers = true;
      GeneralConfiguration gc = new GeneralConfiguration();
      gc.transactionSize = 10;
      AbstractLogLogic logic = createLogic(gc, new LegacyLogicConfiguration(), llc, cache);

      Cluster cluster = new Cluster();
      cluster.addGroup("group", 2);
      logic.manager.getSlaveState().setCluster(cluster);

      cache.put(LogChecker.checkerKey(0, logic.stressor.id), new LogChecker.LastOperation(10, 123));
      cache.put(LogChecker.checkerKey(1, logic.stressor.id), new LogChecker.LastOperation(15, 123));

      assertEquals(logic.getCheckedOperation(logic.stressor.id, 5l), 10l);
   }

   private AbstractLogLogic createLogic(GeneralConfiguration gc, LegacyLogicConfiguration lc, LogLogicConfiguration llc, BasicOperations.Cache cache) throws NoSuchFieldException, IllegalAccessException {
      if (gc == null || lc == null || llc == null || cache == null) {
         throw new IllegalArgumentException("All configuration parameters need to be specified");
      }
      llc.enabled = true;
      BackgroundOpsManager manager = BackgroundOpsManager.getOrCreateInstance(new SlaveState(), "test");
      manager.getSlaveState().setSlaveIndex(0);
      ReflectionUtils.setClassProperty(BackgroundOpsManager.class, manager, "generalConfiguration", gc);
      ReflectionUtils.setClassProperty(BackgroundOpsManager.class, manager, "legacyLogicConfiguration", lc);
      ReflectionUtils.setClassProperty(BackgroundOpsManager.class, manager, "logLogicConfiguration", llc);
      ReflectionUtils.setClassProperty(BackgroundOpsManager.class, manager, "basicCache", cache);
      AbstractLogLogic logic = Mockito.mock(AbstractLogLogic.class, CALLS_REAL_METHODS);
      ReflectionUtils.setClassProperty(AbstractLogLogic.class, logic, "nonTxBasicCache", cache);
      ReflectionUtils.setClassProperty(AbstractLogLogic.class, logic, "basicCache", cache);
      ReflectionUtils.setClassProperty(AbstractLogLogic.class, logic, "keySelectorRandom", mock(Random.class));
      ReflectionUtils.setClassProperty(AbstractLogLogic.class, logic, "keyRange", new Range(0, 1));
      ReflectionUtils.setClassProperty(AbstractLogic.class, logic, "manager", manager);
      ReflectionUtils.setClassProperty(AbstractLogic.class, logic, "log", mock(Log.class));
      ReflectionUtils.setClassProperty(AbstractLogic.class, logic, "keyGenerator", new CacheTestUtils.SimpleStringKeyGenerator());
      ReflectionUtils.setClassProperty(AbstractLogic.class, logic, "transactionSize", gc.transactionSize);
      new Stressor(manager, logic, 0);
      return logic;
   }

   private FailureManager getFailureManager(AbstractLogic logic) throws NoSuchFieldException, IllegalAccessException {
      BackgroundOpsManager manager = ReflectionUtils.getClassProperty(AbstractLogic.class, logic, "manager", BackgroundOpsManager.class);
      return manager.getFailureManager();
   }
}

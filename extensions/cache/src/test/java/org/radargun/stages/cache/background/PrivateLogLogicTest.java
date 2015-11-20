package org.radargun.stages.cache.background;

import org.mockito.Mockito;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.stages.helpers.Range;
import org.radargun.state.SlaveState;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.Transactional;
import org.radargun.util.ReflectionUtils;
import org.radargun.util.TestUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Random;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * @author Matej Cimbora
 */
@Test
public class PrivateLogLogicTest {

   public void testMixedOperations() throws Exception {
      BasicOperations.Cache cache = new TestUtils.BasicOperationsCache();
      GeneralConfiguration gc = new GeneralConfiguration();
      gc.transactionSize = 10;
      PrivateLogLogic logic = createLogic(gc, new LegacyLogicConfiguration(), new LogLogicConfiguration(), cache);

      // operationId 0, PUT
      doReturn(BasicOperations.PUT).when(logic).getOperation(any(Random.class));
      logic.invokeLogic(0);
      assertEquals(cache.get("0"), new PrivateLogValue(0, 0));
      assertEquals(cache.get("-1"), null);
      assertEquals(logic.delayedRemoves.get(0l), null);
      assertEquals(logic.delayedRemoves.get(-1l), null);

      // operationId 0 -> 1, PUT
      logic.operationId++;
      logic.invokeLogic(0);
      assertEquals(cache.get("0"), new PrivateLogValue(0, new long[]{0, 1}));
      assertEquals(cache.get("-1"), null);
      assertEquals(logic.delayedRemoves.get(0l), null);
      assertEquals(logic.delayedRemoves.get(-1l), null);

      // operationId 1 -> 2, REMOVE
      logic.operationId++;
      doReturn(BasicOperations.REMOVE).when(logic).getOperation(any(Random.class));
      logic.invokeLogic(0);
      assertEquals(cache.get("0"), new PrivateLogValue(0, new long[]{0, 1}));
      assertEquals(cache.get("-1"), new PrivateLogValue(0, new long[]{0, 1, 2}));
      assertEquals(logic.delayedRemoves.get(0l).oldValue, new PrivateLogValue(0, new long[]{0, 1}));
      assertEquals(logic.delayedRemoves.get(-1l), null);

      // operationId 2 -> 3, REMOVE
      logic.operationId++;
      logic.invokeLogic(0);
      assertEquals(cache.get("0"), new PrivateLogValue(0, new long[]{0, 1, 2, 3}));
      assertEquals(cache.get("-1"), new PrivateLogValue(0, new long[]{0, 1, 2}));
      assertEquals(logic.delayedRemoves.get(0l), null);
      assertEquals(logic.delayedRemoves.get(-1l).oldValue, new PrivateLogValue(0, new long[]{0, 1, 2}));

      // operationId 3 -> 4, PUT
      logic.operationId++;
      doReturn(BasicOperations.PUT).when(logic).getOperation(any(Random.class));
      logic.invokeLogic(0);
      assertEquals(cache.get("0"), new PrivateLogValue(0, new long[]{0, 1, 2, 3, 4}));
      assertEquals(cache.get("-1"), new PrivateLogValue(0, new long[]{0, 1, 2}));
      assertEquals(logic.delayedRemoves.get(0l), null);
      assertEquals(logic.delayedRemoves.get(-1l).oldValue, new PrivateLogValue(0, new long[]{0, 1, 2}));

      // operationId 4 -> 5, REMOVE
      logic.operationId++;
      doReturn(BasicOperations.REMOVE).when(logic).getOperation(any(Random.class));
      logic.invokeLogic(0);
      assertEquals(cache.get("0"), new PrivateLogValue(0, new long[]{0, 1, 2, 3, 4}));
      assertEquals(cache.get("-1"), new PrivateLogValue(0, new long[]{0, 1, 2, 3, 4, 5}));
      assertEquals(logic.delayedRemoves.get(0l).oldValue, new PrivateLogValue(0, new long[] {0, 1, 2, 3, 4}));
      assertEquals(logic.delayedRemoves.get(-1l), null);

      logic.operationId++;
      doReturn(BasicOperations.PUT).when(logic).getOperation(any(Random.class));
      logic.invokeLogic(0);
      assertEquals(cache.get("0"), new PrivateLogValue(0, new long[]{0, 1, 2, 3, 4, 5, 6}));
      assertEquals(cache.get("-1"), new PrivateLogValue(0, new long[]{0, 1, 2, 3, 4, 5}));
      assertEquals(logic.delayedRemoves.get(0l), null);
      assertEquals(logic.delayedRemoves.get(-1l).oldValue, new PrivateLogValue(0, new long[]{0, 1, 2, 3, 4, 5}));

      // No test errors are expected
      assertNull(getFailureManager(logic).getError(true));
      Assert.assertEquals(ReflectionUtils.getClassProperty(PrivateLogLogic.class, logic, "txModifications", Collection.class).size(), 7);

      doNothing().when(logic).startTransaction();
      logic.ongoingTx = mock(Transactional.Transaction.class);

      logic.afterCommit();

      Map<Long, PrivateLogLogic.OperationTimestampPair> timestamps = ReflectionUtils.getClassProperty(PrivateLogLogic.class, logic, "timestamps", Map.class);
      assertEquals(timestamps.size(), 1);
      assertEquals(timestamps.get(0l).operationId, 6);
      Assert.assertEquals(ReflectionUtils.getClassProperty(PrivateLogLogic.class, logic, "txModifications", Collection.class).size(), 0);
   }

   public void testMixedOperationsNonTx() throws Exception {
      BasicOperations.Cache cache = new TestUtils.BasicOperationsCache();
      PrivateLogLogic logic = createLogic(new GeneralConfiguration(), new LegacyLogicConfiguration(), new LogLogicConfiguration(), cache);
      Map<Long, PrivateLogLogic.OperationTimestampPair> timestamps = ReflectionUtils.getClassProperty(PrivateLogLogic.class, logic, "timestamps", Map.class);

      // operationId 0, PUT
      doReturn(BasicOperations.PUT).when(logic).getOperation(any(Random.class));
      logic.invokeLogic(0);
      assertEquals(cache.get("0"), new PrivateLogValue(0, 0));
      assertEquals(cache.get("-1"), null);
      assertEquals(timestamps.get(0l).operationId, 0);

      // operationId 0 -> 1, PUT
      logic.operationId++;
      logic.invokeLogic(0);
      assertEquals(cache.get("0"), new PrivateLogValue(0, new long[]{0, 1}));
      assertEquals(cache.get("-1"), null);
      assertEquals(timestamps.get(0l).operationId, 1);

      // operationId 1 -> 2, REMOVE
      logic.operationId++;
      doReturn(BasicOperations.REMOVE).when(logic).getOperation(any(Random.class));
      logic.invokeLogic(0);
      assertEquals(cache.get("0"), null);
      assertEquals(cache.get("-1"), new PrivateLogValue(0, new long[]{0, 1, 2}));
      assertEquals(timestamps.get(0l).operationId, 2);

      // operationId 2 -> 3, REMOVE
      logic.operationId++;
      logic.invokeLogic(0);
      assertEquals(cache.get("0"), new PrivateLogValue(0, new long[]{0, 1, 2, 3}));
      assertEquals(cache.get("-1"), null);
      assertEquals(timestamps.get(0l).operationId, 3);

      // operationId 3 -> 4, PUT
      logic.operationId++;
      doReturn(BasicOperations.PUT).when(logic).getOperation(any(Random.class));
      logic.invokeLogic(0);
      assertEquals(cache.get("0"), new PrivateLogValue(0, new long[]{0, 1, 2, 3, 4}));
      assertEquals(cache.get("-1"), null);
      assertEquals(timestamps.get(0l).operationId, 4);

      // operationId 4 -> 5, REMOVE
      logic.operationId++;
      doReturn(BasicOperations.REMOVE).when(logic).getOperation(any(Random.class));
      logic.invokeLogic(0);
      assertEquals(cache.get("0"), null);
      assertEquals(cache.get("-1"), new PrivateLogValue(0, new long[]{0, 1, 2, 3, 4, 5}));
      assertEquals(timestamps.get(0l).operationId, 5);

      logic.operationId++;
      doReturn(BasicOperations.PUT).when(logic).getOperation(any(Random.class));
      logic.invokeLogic(0);
      assertEquals(cache.get("0"), new PrivateLogValue(0, new long[]{0, 1, 2, 3, 4, 5, 6}));
      assertEquals(cache.get("-1"), null);
      assertEquals(timestamps.get(0l).operationId, 6);

      assertEquals(timestamps.size(), 1);
      // No test errors are expected
      assertNull(getFailureManager(logic).getError(true));
   }

   public void testRemoveOperationsFirst() throws Exception {
      BasicOperations.Cache cache = new TestUtils.BasicOperationsCache();
      GeneralConfiguration gc = new GeneralConfiguration();
      gc.transactionSize = 10;
      PrivateLogLogic logic = createLogic(gc, new LegacyLogicConfiguration(), new LogLogicConfiguration(), cache);

      // operationId 0, REMOVE
      doReturn(BasicOperations.REMOVE).when(logic).getOperation(any(Random.class));
      logic.invokeLogic(0);
      assertEquals(cache.get("0"), new PrivateLogValue(0, 0));
      assertEquals(cache.get("-1"), null);
      assertEquals(logic.delayedRemoves.get(0l), null);
      assertEquals(logic.delayedRemoves.get(-1l), null);

      // operationId 0 -> 1, REMOVE
      logic.operationId++;
      logic.invokeLogic(0);
      assertEquals(cache.get("0"), new PrivateLogValue(0, 0));
      assertEquals(cache.get("-1"), new PrivateLogValue(0, new long[]{0, 1}));
      assertEquals(logic.delayedRemoves.get(0l).oldValue, new PrivateLogValue(0, 0));
      assertEquals(logic.delayedRemoves.get(-1l), null);

      // operationId 1 -> 2, REMOVE
      logic.operationId++;
      logic.invokeLogic(0);
      assertEquals(cache.get("0"), new PrivateLogValue(0, new long[] {0, 1, 2}));
      assertEquals(cache.get("-1"), new PrivateLogValue(0, new long[]{0, 1}));
      assertEquals(logic.delayedRemoves.get(0l), null);
      assertEquals(logic.delayedRemoves.get(-1l).oldValue, new PrivateLogValue(0, new long[] {0, 1}));

      // No test errors are expected
      assertNull(getFailureManager(logic).getError(true));
      Assert.assertEquals(ReflectionUtils.getClassProperty(PrivateLogLogic.class, logic, "txModifications", Collection.class).size(), 3);

      doNothing().when(logic).startTransaction();
      logic.ongoingTx = mock(Transactional.Transaction.class);

      logic.afterCommit();

      Map<Long, PrivateLogLogic.OperationTimestampPair> timestamps = ReflectionUtils.getClassProperty(PrivateLogLogic.class, logic, "timestamps", Map.class);
      assertEquals(timestamps.size(), 1);
      assertEquals(timestamps.get(0l).operationId, 2);
      Assert.assertEquals(ReflectionUtils.getClassProperty(PrivateLogLogic.class, logic, "txModifications", Collection.class).size(), 0);
   }

   @Test(expectedExceptions = UnsupportedOperationException.class)
   public void testGetFails() throws Exception {
      BasicOperations.Cache cache = new TestUtils.BasicOperationsCache();
      PrivateLogLogic logic = createLogic(new GeneralConfiguration(), new LegacyLogicConfiguration(), new LogLogicConfiguration(), cache);

      // operationId 0, GET
      doReturn(BasicOperations.GET).when(logic).getOperation(any(Random.class));
      logic.invokeLogic(0);
   }

   /**
    * Primary doesn't contain the operation, backup null
    */
   public void testStaleReadDetected1() throws Exception {
      BasicOperations.Cache cache = new TestUtils.BasicOperationsCache();
      GeneralConfiguration gc = new GeneralConfiguration();
      gc.transactionSize = 10;
      PrivateLogLogic logic = createLogic(gc, new LegacyLogicConfiguration(), new LogLogicConfiguration(), cache);

      doReturn(BasicOperations.PUT).when(logic).getOperation(any(Random.class));

      Map<Long, PrivateLogLogic.OperationTimestampPair> timestamps = ReflectionUtils.getClassProperty(PrivateLogLogic.class, logic, "timestamps", Map.class);
      timestamps.put(0l, new PrivateLogLogic.OperationTimestampPair(5l, 123l));
      cache.put("0", new PrivateLogValue(0, new long[]{0, 1, 2}));
      logic.operationId = 3;

      logic.invokeLogic(0);

      assertEquals(getFailureManager(logic).getStaleReads(), 1);
   }

   /**
    * Neither primary contains the operation, nor backup
    */
   public void testStaleReadDetected2() throws Exception {
      BasicOperations.Cache cache = new TestUtils.BasicOperationsCache();
      GeneralConfiguration gc = new GeneralConfiguration();
      gc.transactionSize = 10;
      PrivateLogLogic logic = createLogic(gc, new LegacyLogicConfiguration(), new LogLogicConfiguration(), cache);

      doReturn(BasicOperations.PUT).when(logic).getOperation(any(Random.class));

      Map<Long, PrivateLogLogic.OperationTimestampPair> timestamps = ReflectionUtils.getClassProperty(PrivateLogLogic.class, logic, "timestamps", Map.class);
      timestamps.put(0l, new PrivateLogLogic.OperationTimestampPair(5l, 123l));
      cache.put("0", new PrivateLogValue(0, new long[]{0, 1, 2}));
      cache.put("-1", new PrivateLogValue(0, new long[]{0, 1, 2, 3}));
      logic.operationId = 4;
      logic.invokeLogic(0);

      assertEquals(getFailureManager(logic).getStaleReads(), 1);
   }

   /**
    * Primary null, backup doesn't contain the operation
    */
   public void testStaleReadDetected3() throws Exception {
      BasicOperations.Cache cache = new TestUtils.BasicOperationsCache();
      GeneralConfiguration gc = new GeneralConfiguration();
      gc.transactionSize = 10;
      PrivateLogLogic logic = createLogic(gc, new LegacyLogicConfiguration(), new LogLogicConfiguration(), cache);

      doReturn(BasicOperations.PUT).when(logic).getOperation(any(Random.class));

      Map<Long, PrivateLogLogic.OperationTimestampPair> timestamps = ReflectionUtils.getClassProperty(PrivateLogLogic.class, logic, "timestamps", Map.class);
      timestamps.put(0l, new PrivateLogLogic.OperationTimestampPair(5l, 123l));
      cache.put("-1", new PrivateLogValue(0, new long[]{0, 1, 2, 3}));
      logic.operationId = 4;

      logic.invokeLogic(0);

      assertEquals(getFailureManager(logic).getStaleReads(), 1);
   }

   /**
    * Both primary and backup null
    */
   public void testStaleReadDetected4() throws Exception {
      BasicOperations.Cache cache = new TestUtils.BasicOperationsCache();
      GeneralConfiguration gc = new GeneralConfiguration();
      gc.transactionSize = 10;
      PrivateLogLogic logic = createLogic(gc, new LegacyLogicConfiguration(), new LogLogicConfiguration(), cache);

      doReturn(BasicOperations.PUT).when(logic).getOperation(any(Random.class));

      Map<Long, PrivateLogLogic.OperationTimestampPair> timestamps = ReflectionUtils.getClassProperty(PrivateLogLogic.class, logic, "timestamps", Map.class);
      timestamps.put(0l, new PrivateLogLogic.OperationTimestampPair(5l, 123l));
      logic.operationId = 4;

      logic.invokeLogic(0);

      assertEquals(getFailureManager(logic).getStaleReads(), 1);
   }

   /**
    * Using logLogicConfiguration.writeApplyMaxDelay avoids stale reads
    */
   public void testStaleReadDetected5() throws Exception {
      BasicOperations.Cache cache = new TestUtils.BasicOperationsCache();
      GeneralConfiguration gc = new GeneralConfiguration();
      gc.transactionSize = 10;
      LogLogicConfiguration llc = new LogLogicConfiguration();
      llc.writeApplyMaxDelay = 100;
      PrivateLogLogic logic = createLogic(gc, new LegacyLogicConfiguration(), llc, cache);

      doReturn(BasicOperations.PUT).when(logic).getOperation(any(Random.class));

      Map<Long, PrivateLogLogic.OperationTimestampPair> timestamps = ReflectionUtils.getClassProperty(PrivateLogLogic.class, logic, "timestamps", Map.class);
      timestamps.put(0l, new PrivateLogLogic.OperationTimestampPair(5l, 123l));
      cache.put("0", new PrivateLogValue(0, new long[]{0, 1, 2}));
      logic.operationId = 3;

      logic.invokeLogic(0);

      assertEquals(getFailureManager(logic).getStaleReads(), 0);
   }

   /**
    * Test logLogicConfiguration.valueMaxSize attained & modifying the same key multiple times within the same transaction. Provided
    * timestamps are updated only after transaction finishes, we may lose last operation if it has been already checked by checkers,
    * leading to stale reads if PrivateLogLogic.txModificationKeyIds are not used. Primary key test.
    */
   public void testStaleReadDetected6() throws Exception {
      BasicOperations.Cache txCache = new TestUtils.BasicOperationsCache();
      GeneralConfiguration gc = new GeneralConfiguration();
      gc.transactionSize = 10;
      LogLogicConfiguration llc = new LogLogicConfiguration();
      llc.valueMaxSize = 5;
      PrivateLogLogic logic = createLogic(gc, new LegacyLogicConfiguration(), llc, txCache);

      doReturn(BasicOperations.PUT).when(logic).getOperation(any(Random.class));
      doReturn(4l).when(logic).getCheckedOperation(anyInt(), anyLong());

      Map<Long, PrivateLogLogic.OperationTimestampPair> timestamps = ReflectionUtils.getClassProperty(PrivateLogLogic.class, logic, "timestamps", Map.class);
      timestamps.put(0l, new PrivateLogLogic.OperationTimestampPair(4l, 123l));
      txCache.put("0", new PrivateLogValue(0, new long[]{0, 1, 2, 3, 4}));
      logic.operationId = 5;
      logic.invokeLogic(0);

      logic.operationId++;
      logic.invokeLogic(0);

      assertEquals(getFailureManager(logic).getStaleReads(), 0);
   }

   /**
    * Test logLogicConfiguration.valueMaxSize attained & modifying the same key multiple times within the same transaction. Provided
    * timestamps are updated only after transaction finishes, we may lose last operation if it has been already checked by checkers,
    * leading to stale reads if PrivateLogLogic.txModificationKeyIds are not used. Backup key test.
    */
   public void testStaleReadDetected7() throws Exception {
      BasicOperations.Cache cache = new TestUtils.BasicOperationsCache();
      GeneralConfiguration gc = new GeneralConfiguration();
      gc.transactionSize = 10;
      LogLogicConfiguration llc = new LogLogicConfiguration();
      llc.valueMaxSize = 5;
      PrivateLogLogic logic = createLogic(gc, new LegacyLogicConfiguration(), llc, cache);

      doReturn(BasicOperations.PUT).when(logic).getOperation(any(Random.class));
      doReturn(4l).when(logic).getCheckedOperation(anyInt(), anyLong());

      Map<Long, PrivateLogLogic.OperationTimestampPair> timestamps = ReflectionUtils.getClassProperty(PrivateLogLogic.class, logic, "timestamps", Map.class);
      timestamps.put(0l, new PrivateLogLogic.OperationTimestampPair(4l, 123l));
      cache.put("-1", new PrivateLogValue(0, new long[]{0, 1, 2, 3, 4}));
      logic.operationId = 5;
      logic.invokeLogic(0);

      logic.operationId++;
      logic.invokeLogic(0);

      assertEquals(getFailureManager(logic).getStaleReads(), 0);
   }

   private PrivateLogLogic createLogic(GeneralConfiguration gc, LegacyLogicConfiguration lc, LogLogicConfiguration llc, BasicOperations.Cache cache) throws NoSuchFieldException, IllegalAccessException {
      if (gc == null || lc == null || llc == null || cache == null) {
         throw new IllegalArgumentException("All configuration parameters need to be specified");
      }
      llc.enabled = true;
      SlaveState slaveState = new SlaveState();
      slaveState.put(KeyGenerator.KEY_GENERATOR, new TestUtils.SimpleStringKeyGenerator());
      BackgroundOpsManager manager = BackgroundOpsManager.getOrCreateInstance(slaveState, "test");
      ReflectionUtils.setClassProperty(BackgroundOpsManager.class, manager, "generalConfiguration", gc);
      ReflectionUtils.setClassProperty(BackgroundOpsManager.class, manager, "legacyLogicConfiguration", lc);
      ReflectionUtils.setClassProperty(BackgroundOpsManager.class, manager, "logLogicConfiguration", llc);
      ReflectionUtils.setClassProperty(BackgroundOpsManager.class, manager, "basicCache", cache);
      Range range = new Range(0, 1);
      PrivateLogLogic logic = Mockito.spy(new PrivateLogLogic(manager, range));
      ReflectionUtils.setClassProperty(AbstractLogLogic.class, logic, "nonTxBasicCache", cache);
      ReflectionUtils.setClassProperty(AbstractLogLogic.class, logic, "basicCache", cache);
      new Stressor(manager, logic, 0);
      return logic;
   }

   private FailureManager getFailureManager(AbstractLogic logic) throws NoSuchFieldException, IllegalAccessException {
      BackgroundOpsManager manager = ReflectionUtils.getClassProperty(AbstractLogic.class, logic, "manager", BackgroundOpsManager.class);
      return manager.getFailureManager();
   }
}

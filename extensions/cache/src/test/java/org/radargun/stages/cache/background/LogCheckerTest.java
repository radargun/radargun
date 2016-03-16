package org.radargun.stages.cache.background;

import org.radargun.logging.Log;
import org.radargun.stages.helpers.Range;
import org.radargun.state.SlaveState;
import org.radargun.traits.BasicOperations;
import org.radargun.util.CacheTestUtils;
import org.radargun.util.CacheTraitRepository;
import org.radargun.util.ReflectionUtils;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * @author Matej Cimbora
 */
@Test
public class LogCheckerTest {

   public void testRunContainsOp() throws Exception {
      BasicOperations.Cache cache = new CacheTraitRepository.BasicOperationsCache<>();
      LogChecker logChecker = createLogChecker(new GeneralConfiguration(), new LegacyLogicConfiguration(), new LogLogicConfiguration(), cache);

      assertEquals(logChecker.stressorRecordPool.getAvailableRecords().size(), 1);
      doReturn(false).doReturn(true).when(logChecker).isInterrupted();

      StressorRecord stressorRecord = logChecker.stressorRecordPool.take();
      stressorRecord.currentOp = 3;
      logChecker.stressorRecordPool.add(stressorRecord);

      PrivateLogValue logValue = new PrivateLogValue(0, new long[] {1, 2, 3, 4});
      doReturn(logValue).when(logChecker).findValue(stressorRecord);
      doReturn(true).when(logChecker).containsOperation(logValue, stressorRecord);

      assertEquals(stressorRecord.confirmations.size(), 0);
      assertEquals(stressorRecord.getLastUnsuccessfulCheckTimestamp(), Long.MIN_VALUE);
      long lastSuccessfulCheckTimestamp = stressorRecord.getLastSuccessfulCheckTimestamp();

      logChecker.run();

      assertEquals(stressorRecord.confirmations.size(), 0);
      assertEquals(stressorRecord.getLastUnsuccessfulCheckTimestamp(), Long.MIN_VALUE);
      assertNotEquals(lastSuccessfulCheckTimestamp, stressorRecord.getLastSuccessfulCheckTimestamp());
      assertEquals(stressorRecord.currentOp, 4);

      assertNull(logChecker.failureManager.getError(true));
   }

   public void testRunContainsOpLogCheckerKey() throws Exception {
      BasicOperations.Cache cache = new CacheTraitRepository.BasicOperationsCache<>();
      LogChecker logChecker = createLogChecker(new GeneralConfiguration(), new LegacyLogicConfiguration(), new LogLogicConfiguration(), cache);

      assertEquals(logChecker.stressorRecordPool.getAvailableRecords().size(), 1);
      doReturn(false).doReturn(true).when(logChecker).isInterrupted();

      cache.put(LogChecker.checkerKey(0, 0), new LogChecker.LastOperation(3, 123));
      StressorRecord stressorRecord = logChecker.stressorRecordPool.take();
      logChecker.stressorRecordPool.add(stressorRecord);

      logChecker.run();

      verify(logChecker, times(1)).newRecord(stressorRecord, 3, 123);
   }

   public void testRunDoesntContainOp() throws Exception {
      BasicOperations.Cache cache = new CacheTraitRepository.BasicOperationsCache<>();
      LogChecker logChecker = createLogChecker(new GeneralConfiguration(), new LegacyLogicConfiguration(), new LogLogicConfiguration(), cache);

      assertEquals(logChecker.stressorRecordPool.getAvailableRecords().size(), 1);
      doReturn(false).doReturn(true).when(logChecker).isInterrupted();

      StressorRecord stressorRecord = logChecker.stressorRecordPool.take();
      stressorRecord.currentOp = 5;
      logChecker.stressorRecordPool.add(stressorRecord);

      PrivateLogValue logValue = new PrivateLogValue(0, new long[] {1, 2, 3, 4});
      doReturn(logValue).when(logChecker).findValue(stressorRecord);
      doReturn(false).when(logChecker).containsOperation(logValue, stressorRecord);

      assertEquals(stressorRecord.confirmations.size(), 0);
      assertEquals(stressorRecord.getLastUnsuccessfulCheckTimestamp(), Long.MIN_VALUE);
      long lastSuccessfulCheckTimestamp = stressorRecord.getLastSuccessfulCheckTimestamp();

      logChecker.run();

      assertEquals(stressorRecord.confirmations.size(), 0);
      assertNotEquals(stressorRecord.getLastUnsuccessfulCheckTimestamp(), Long.MIN_VALUE);
      assertEquals(lastSuccessfulCheckTimestamp, stressorRecord.getLastSuccessfulCheckTimestamp());

      assertNull(logChecker.failureManager.getError(true));
   }

   public void testRunDoesntContainOpConfirmationHigher() throws Exception {
      BasicOperations.Cache cache = new CacheTraitRepository.BasicOperationsCache<>();
      LogChecker logChecker = createLogChecker(new GeneralConfiguration(), new LegacyLogicConfiguration(), new LogLogicConfiguration(), cache);

      assertEquals(logChecker.stressorRecordPool.getAvailableRecords().size(), 1);
      doReturn(false).doReturn(true).when(logChecker).isInterrupted();

      StressorRecord stressorRecord = spy(logChecker.stressorRecordPool.take());
      stressorRecord.currentOp = 4;
      stressorRecord.addConfirmation(5l, 123l);
      logChecker.stressorRecordPool.add(stressorRecord);

      PrivateLogValue logValue = new PrivateLogValue(0, new long[] {1, 2, 3, 4});
      doReturn(logValue).when(logChecker).findValue(stressorRecord);
      doReturn(false).when(logChecker).containsOperation(logValue, stressorRecord);
      doReturn(false).when(stressorRecord).hasNotification(anyLong());

      assertEquals(stressorRecord.confirmations.size(), 1);
      assertEquals(stressorRecord.getLastUnsuccessfulCheckTimestamp(), Long.MIN_VALUE);
      long lastSuccessfulCheckTimestamp = stressorRecord.getLastSuccessfulCheckTimestamp();

      logChecker.run();

      assertEquals(stressorRecord.confirmations.size(), 1);
      assertNotEquals(stressorRecord.getLastUnsuccessfulCheckTimestamp(), Long.MIN_VALUE);
      assertEquals(lastSuccessfulCheckTimestamp, stressorRecord.getLastSuccessfulCheckTimestamp());

      assertEquals(logChecker.failureManager.getMissingOperations(), 1);
      assertEquals(logChecker.failureManager.getMissingNotifications(), 1);
   }

   public void testRunDoesntContainOpConfirmationLower() throws Exception {
      BasicOperations.Cache cache = new CacheTraitRepository.BasicOperationsCache<>();
      LogChecker logChecker = createLogChecker(new GeneralConfiguration(), new LegacyLogicConfiguration(), new LogLogicConfiguration(), cache);

      assertEquals(logChecker.stressorRecordPool.getAvailableRecords().size(), 1);
      doReturn(false).doReturn(true).when(logChecker).isInterrupted();

      StressorRecord stressorRecord = spy(logChecker.stressorRecordPool.take());
      stressorRecord.currentOp = 4;
      stressorRecord.addConfirmation(3l, 123l);
      logChecker.stressorRecordPool.add(stressorRecord);

      PrivateLogValue logValue = new PrivateLogValue(0, new long[] {1, 2, 3, 4});
      doReturn(logValue).when(logChecker).findValue(stressorRecord);
      doReturn(false).when(logChecker).containsOperation(logValue, stressorRecord);
      doReturn(false).when(stressorRecord).hasNotification(anyLong());

      assertEquals(stressorRecord.confirmations.size(), 1);
      assertEquals(stressorRecord.getLastUnsuccessfulCheckTimestamp(), Long.MIN_VALUE);
      long lastSuccessfulCheckTimestamp = stressorRecord.getLastSuccessfulCheckTimestamp();

      logChecker.run();

      assertEquals(stressorRecord.confirmations.size(), 1);
      assertNotEquals(stressorRecord.getLastUnsuccessfulCheckTimestamp(), Long.MIN_VALUE);
      assertEquals(lastSuccessfulCheckTimestamp, stressorRecord.getLastSuccessfulCheckTimestamp());

      assertNull(logChecker.failureManager.getError(true));
   }

   public void testRunDoesntContainOpConfirmationUnsuccessful() throws Exception {
      BasicOperations.Cache cache = new CacheTraitRepository.BasicOperationsCache<>();
      LogChecker logChecker = createLogChecker(new GeneralConfiguration(), new LegacyLogicConfiguration(), new LogLogicConfiguration(), cache);

      assertEquals(logChecker.stressorRecordPool.getAvailableRecords().size(), 1);
      doReturn(false).doReturn(true).when(logChecker).isInterrupted();

      cache.put(LogChecker.lastOperationKey(0), new LogChecker.LastOperation(5, 123));

      StressorRecord stressorRecord = spy(logChecker.stressorRecordPool.take());
      stressorRecord.currentOp = 4;
      stressorRecord.setLastUnsuccessfulCheckTimestamp(123);
      logChecker.stressorRecordPool.add(stressorRecord);

      PrivateLogValue logValue = new PrivateLogValue(0, new long[] {1, 2, 3, 4});
      doReturn(logValue).when(logChecker).findValue(stressorRecord);
      doReturn(false).when(logChecker).containsOperation(logValue, stressorRecord);
      doReturn(false).when(stressorRecord).hasNotification(anyLong());

      assertEquals(stressorRecord.confirmations.size(), 0);
      assertEquals(stressorRecord.getLastUnsuccessfulCheckTimestamp(), 123);
      long lastSuccessfulCheckTimestamp = stressorRecord.getLastSuccessfulCheckTimestamp();

      logChecker.run();

      assertEquals(stressorRecord.confirmations.size(), 1);
      assertNotEquals(stressorRecord.getLastUnsuccessfulCheckTimestamp(), 123);
      assertEquals(lastSuccessfulCheckTimestamp, stressorRecord.getLastSuccessfulCheckTimestamp());

      assertEquals(logChecker.failureManager.getMissingOperations(), 1);
      assertEquals(logChecker.failureManager.getMissingNotifications(), 1);
   }

   public void testCheckIgnoreRecord() throws NoSuchFieldException, IllegalAccessException {
      BasicOperations.Cache cache = new CacheTraitRepository.BasicOperationsCache<>();
      LogLogicConfiguration llc = new LogLogicConfiguration();
      llc.ignoreDeadCheckers = true;
      LogChecker logChecker = createLogChecker(new GeneralConfiguration(), new LegacyLogicConfiguration(), llc, cache);

      StressorRecord stressorRecord = spy(new StressorRecord(0, new Range(0, 1)));
      assertEquals(stressorRecord.currentOp, 0);

      cache.put(LogChecker.ignoredKey(0, 0), 2l);

      assertEquals(logChecker.checkIgnoreRecord(stressorRecord), true);
      assertEquals(stressorRecord.currentOp, 3l);
   }

   private LogChecker createLogChecker(GeneralConfiguration gc, LegacyLogicConfiguration lc, LogLogicConfiguration llc, BasicOperations.Cache cache) throws NoSuchFieldException, IllegalAccessException {
      if (gc == null || lc == null || llc == null || cache == null) {
         throw new IllegalArgumentException("All configuration parameters need to be specified");
      }
      llc.enabled = true;
      SlaveState slaveState = new SlaveState();
      BackgroundOpsManager manager = BackgroundOpsManager.getOrCreateInstance(slaveState, "test");
      ReflectionUtils.setClassProperty(BackgroundOpsManager.class, manager, "generalConfiguration", gc);
      ReflectionUtils.setClassProperty(BackgroundOpsManager.class, manager, "legacyLogicConfiguration", lc);
      ReflectionUtils.setClassProperty(BackgroundOpsManager.class, manager, "logLogicConfiguration", llc);
      ReflectionUtils.setClassProperty(BackgroundOpsManager.class, manager, "basicCache", cache);
      List<StressorRecord> stressorRecords = new ArrayList<>(1);
      stressorRecords.add(new StressorRecord(0, new Range(0, 1)));
      StressorRecordPool pool = new StressorRecordPool(1, stressorRecords, manager);
      LogChecker logChecker = mock(LogChecker.class, CALLS_REAL_METHODS);
      ReflectionUtils.setClassProperty(LogChecker.class, logChecker, "failureManager", manager.getFailureManager());
      ReflectionUtils.setClassProperty(LogChecker.class, logChecker, "basicCache", manager.getBasicCache());
      ReflectionUtils.setClassProperty(LogChecker.class, logChecker, "stressorRecordPool", pool);
      ReflectionUtils.setClassProperty(LogChecker.class, logChecker, "log", mock(Log.class));
      ReflectionUtils.setClassProperty(LogChecker.class, logChecker, "keyGenerator", new CacheTestUtils.SimpleStringKeyGenerator());
      ReflectionUtils.setClassProperty(LogChecker.class, logChecker, "logLogicConfiguration", llc);
      return logChecker;
   }
}

package org.radargun.stages.cache.background;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.CacheListeners;

import java.util.Arrays;
import java.util.Collection;

/**
 * Listener handling {@link org.radargun.stages.cache.background.StressorRecord} notifications.
 *
 * @author Matej Cimbora
 * @author Radim Vansa
 */
public class StressorRecordNotifier implements CacheListeners.UpdatedListener, CacheListeners.CreatedListener {

   private static final Log log = LogFactory.getLog(StressorRecordNotifier.class);

   private BackgroundOpsManager manager;

   public StressorRecordNotifier(BackgroundOpsManager manager) {
      this.manager = manager;
   }

   @Override
   public void created(Object key, Object value) {
      log.trace("Created " + key + " -> " + value);
      modified(key, value);
   }

   @Override
   public void updated(Object key, Object value) {
      log.trace("Updated " + key + " -> " + value);
      modified(key, value);
   }

   private void modified(Object key, Object value) {
      if (value instanceof PrivateLogValue) {
         PrivateLogValue logValue = (PrivateLogValue) value;
         notify(logValue.getThreadId(), logValue.getOperationId(logValue.size() - 1), key);
      } else if (value instanceof SharedLogValue) {
         SharedLogValue logValue = (SharedLogValue) value;
         int last = logValue.size() - 1;
         notify(logValue.getThreadId(last), logValue.getOperationId(last), key);
      } else if (key instanceof String && ((String) key).startsWith(LogChecker.LAST_OPERATION_PREFIX)) {
         int threadId = Integer.parseInt(((String) key).substring(LogChecker.LAST_OPERATION_PREFIX.length()));
         LogChecker.LastOperation last = (LogChecker.LastOperation) value;
         requireNotify(threadId, last.getOperationId() + 1);
      }
   }

   private void notify(int threadId, long operationId, Object key) {
      StressorRecord record = manager.getStressorRecordPool().getAllRecords().get(threadId);
      record.notify(operationId, key);
   }

   private void requireNotify(int threadId, long operationId) {
      StressorRecord record = manager.getStressorRecordPool().getAllRecords().get(threadId);
      record.requireNotify(operationId);
   }

   protected void registerListeners(boolean sync) {
      if (!manager.getLogLogicConfiguration().isCheckNotifications()) {
         return;
      }
      CacheListeners listeners = manager.getListeners();
      if (listeners == null) {
         throw new IllegalArgumentException("Service does not support cache listeners");
      }
      Collection<CacheListeners.Type> supported = listeners.getSupportedListeners();
      if (!supported.containsAll(Arrays.asList(CacheListeners.Type.CREATED, CacheListeners.Type.UPDATED))) {
         throw new IllegalArgumentException("Service does not support required listener types; supported are: " + supported);
      }
      String cacheName = manager.getGeneralConfiguration().getCacheName();
      manager.getListeners().addCreatedListener(cacheName, this, sync);
      manager.getListeners().addUpdatedListener(cacheName, this, sync);
   }
}

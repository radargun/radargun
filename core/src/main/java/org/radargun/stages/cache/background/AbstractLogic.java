package org.radargun.stages.cache.background;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.traits.Transactional;

/**
 * Common operations for all logics.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class AbstractLogic implements Logic {

   protected final Log log = LogFactory.getLog(getClass());
   protected final boolean trace = log.isTraceEnabled();

   protected BackgroundOpsManager manager;
   protected KeyGenerator keyGenerator;
   protected final int transactionSize;
   protected final Transactional.Resource txCache;
   protected Stressor stressor;

   protected AbstractLogic(BackgroundOpsManager manager) {
      this.manager = manager;
      this.keyGenerator = manager.getKeyGenerator();
      this.transactionSize = manager.getGeneralConfiguration().getTransactionSize();
      this.txCache = manager.getTransactionalCache();
   }

   @Override
   public void finish() {
      if (transactionSize > 0) {
         try {
            txCache.endTransaction(false);
         } catch (Exception e) {
            log.error("Error while ending transaction", e);
         }
      }
   }

   public void setStressor(Stressor stressor) {
      this.stressor = stressor;
   }

   protected static InterruptedException findInterruptionCause(Throwable eParent, Throwable e) {
      if (e == null || eParent == e) {
         return null;
      } else if (e instanceof InterruptedException) {
         return (InterruptedException) e;
      } else {
         return findInterruptionCause(e, e.getCause());
      }
   }
}

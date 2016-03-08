package org.radargun.stages.cache.background;

import java.util.Random;

import org.radargun.Operation;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.Transactional;

/**
 * Common operations for all logic types.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class AbstractLogic implements Logic {

   protected static final Operation GET_NULL = BasicOperations.GET.derive("Null");
   protected final Log log = LogFactory.getLog(getClass());
   protected final boolean trace = log.isTraceEnabled();

   protected BackgroundOpsManager manager;
   protected KeyGenerator keyGenerator;
   protected final int operations;
   protected final int transactionSize;
   protected Stressor stressor;
   protected Transactional.Transaction ongoingTx;

   protected AbstractLogic(BackgroundOpsManager manager) {
      this.manager = manager;
      this.keyGenerator = manager.getKeyGenerator();
      this.transactionSize = manager.getGeneralConfiguration().getTransactionSize();
      this.operations = manager.getGeneralConfiguration().puts + manager.getGeneralConfiguration().gets + manager.getGeneralConfiguration().removes;
   }

   @Override
   public void finish() {
      if (transactionSize > 0 && ongoingTx != null) {
         try {
            ongoingTx.rollback();
         } catch (Exception e) {
            log.error("Error while ending transaction", e);
         }
      }
   }

   @Override
   public void setStressor(Stressor stressor) {
      this.stressor = stressor;
   }

   public Operation getOperation(Random rand) {
      int r = rand.nextInt(operations);
      if (r < manager.getGeneralConfiguration().gets) {
         return BasicOperations.GET;
      } else if (r < manager.getGeneralConfiguration().gets + manager.getGeneralConfiguration().puts) {
         return BasicOperations.PUT;
      } else return BasicOperations.REMOVE;
   }

}

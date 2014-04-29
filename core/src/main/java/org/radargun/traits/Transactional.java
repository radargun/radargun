package org.radargun.traits;

import org.radargun.Operation;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Trait(doc = "Trait providing transactional operations.")
public interface Transactional {
   String TRAIT = Transactional.class.getSimpleName();
   Operation BEGIN =    Operation.register(TRAIT + ".Begin");
   Operation COMMIT =   Operation.register(TRAIT + ".Commit");
   Operation ROLLBACK = Operation.register(TRAIT + ".Rollback");
   Operation DURATION = Operation.register(TRAIT + ".Duration");

   /**
    * @return True if the cache is configured to use transactions.
    */
   boolean isTransactional(String resourceName);

   Resource getResource(String resourceName);

   interface Resource {
      /**
       * Starts a transaction against the cache. All following operations will
       * take place in the scope of the transaction started. The transaction will
       * be completed by invoking {@link #endTransaction(boolean)}.
       */
      void startTransaction();

      /**
       * Called in conjunction with {@link #startTransaction()} in order to complete
       * a transaction by either committing or rolling it back.
       * @param successful commit or rollback?
       */
      void endTransaction(boolean successful);
   }
}

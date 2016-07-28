package org.radargun.traits;

import org.radargun.Operation;

@Trait(doc = "Trait providing transactional operations.")
public interface Transactional {
   String TRAIT = Transactional.class.getSimpleName();
   Operation BEGIN = Operation.register(TRAIT + ".Begin");
   Operation COMMIT = Operation.register(TRAIT + ".Commit");
   Operation ROLLBACK = Operation.register(TRAIT + ".Rollback");
   Operation DURATION = Operation.register(TRAIT + ".Duration");

   public enum Configuration {
      /**
       * No transactions can be executed on the resource
       */
      NON_TRANSACTIONAL,
      /**
       * Operations on this cache will be executed in transaction only if explicitly requested
       */
      TRANSACTIONS_ENABLED,
      /**
       * All operations should be executed in transaction
       */
      TRANSACTIONAL
   }

   /**
    * @return True if the resource is configured to be able to use transactions.
    */
   Configuration getConfiguration(String resourceName);

   /**
    * Get a new object representing a transaction.
    * @return
    */
   Transaction getTransaction();

   /**
    * Each instance of transaction should be used only for single begin() ... commit() || rollback().
    */
   interface Transaction {
      /**
       * Retrieve a wrapped version of this resource that can be used for transactional operations.
       * ({@link #begin()} must not be called until at least one resource was wrapped.
       * The behaviour of operations executed within the scope of the transaction on non-wrapped
       * resources is undefined.
       *
       * @param resource Usually some object retrieved from another Trait
       * @param <T>
       * @return
       * @throws IllegalArgumentException If the resource cannot be wrapped.
       */
      <T> T wrap(T resource);

      /**
       * Starts a transaction. All following operations on wrapped resources specified
       * when retrieving this transaction will take place in the scope of this
       * transaction. The transaction will be completed by invoking
       * {@link #commit()} or {@link #rollback()}
       */
      void begin();

      /**
       * Complete the transaction by committing (changes are persisted).
       */
      void commit();

      /**
       * Complete the transaction by rolling it back (changes are discarded).
       */
      void rollback();
   }
}

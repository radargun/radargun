package org.radargun.service;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

import org.radargun.traits.Transactional;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanTransactional implements Transactional {

   protected final InfinispanEmbeddedService service;
   protected final boolean enlistExtraXAResource;

   public InfinispanTransactional(InfinispanEmbeddedService service) {
      this.service = service;
      this.enlistExtraXAResource = service.enlistExtraXAResource;
   }

   @Override
   public boolean isTransactional(String cacheName) {
      return service.isCacheTransactional(service.getCache(cacheName));
   }

   @Override
   public Transactional.Resource getResource(String cacheName) {
      return new Resource(service.getCache(cacheName).getAdvancedCache().getTransactionManager());
   }

   protected class Resource implements Transactional.Resource {
      protected final TransactionManager tm;

      public Resource(TransactionManager transactionManager) {
         if (transactionManager == null) throw new NullPointerException();
         this.tm = transactionManager;
      }

      @Override
      public void startTransaction() {
         try {
            tm.begin();
            Transaction transaction = tm.getTransaction();
            if (enlistExtraXAResource) {
               transaction.enlistResource(new DummyXAResource());
            }
         }
         catch (Exception e) {
            throw new RuntimeException(e);
         }
      }

      @Override
      public void endTransaction(boolean successful) {
         try {
            if (successful)
               tm.commit();
            else
               tm.rollback();
         }
         catch (Exception e) {
            throw new RuntimeException(e);
         }
      }
   }
}

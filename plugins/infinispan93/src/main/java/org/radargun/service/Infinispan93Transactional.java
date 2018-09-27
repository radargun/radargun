package org.radargun.service;

import javax.transaction.TransactionManager;

/**
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
public class Infinispan93Transactional extends Infinispan51Transactional {

   public Infinispan93Transactional(InfinispanTransactionalService service) {
      super(service);
   }

   @Override
   protected <T> TransactionManager getTransactionManager(T resource) {
      if (resource instanceof HotRodOperations.HotRodCache) {
         return ((HotRodOperations.HotRodCache) resource).noReturn.getTransactionManager();
      } else {
         return super.getTransactionManager(resource);
      }
   }
}

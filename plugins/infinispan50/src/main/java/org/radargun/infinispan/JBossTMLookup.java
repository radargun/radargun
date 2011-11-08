package org.radargun.infinispan;

import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.arjPropertyManager;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;
import org.infinispan.transaction.lookup.TransactionManagerLookup;

import javax.transaction.TransactionManager;

/**
 * @author Mircea Markus
 */
public class JBossTMLookup implements TransactionManagerLookup {

   @Override
   public TransactionManager getTransactionManager() throws Exception {
      arjPropertyManager.getCoordinatorEnvironmentBean().setActionStore(com.arjuna.ats.internal.arjuna.objectstore.VolatileStore.class.getName());
      BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "communicationStore").setObjectStoreType(com.arjuna.ats.internal.arjuna.objectstore.VolatileStore.class.getName());
      arjPropertyManager.getObjectStoreEnvironmentBean().setObjectStoreType(com.arjuna.ats.internal.arjuna.objectstore.VolatileStore.class.getName());
      return com.arjuna.ats.jta.TransactionManager.transactionManager();
   }
}

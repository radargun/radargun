package org.radargun.service;

import com.hazelcast.transaction.TransactionContext;
import org.radargun.Service;

/**
 * An implementation of CacheWrapper that uses Hazelcast instance as an underlying implementation.
 * @author Maido Kaara
 */
@Service(doc = "Hazelcast")
public class Hazelcast3Service extends HazelcastService {

    ThreadLocal<TransactionContext> transactionContext = new ThreadLocal<TransactionContext>();

    @Override
    public void startTransaction() {
        try {
            TransactionContext newTransactionContext = hazelcastInstance.newTransactionContext();
            transactionContext.set(newTransactionContext);
            newTransactionContext.beginTransaction();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void endTransaction(boolean successful) {
        try {
            TransactionContext tc = transactionContext.get();
            transactionContext.remove();
            if (successful) {
                tc.commitTransaction();
            } else {
                tc.rollbackTransaction();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}

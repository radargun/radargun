package org.radargun.tpcc.transaction;

import org.radargun.CacheWrapper;

/**
 * @author peluso@gsd.inesc-id.pt , peluso@dis.uniroma1.it
 */
public interface TpccTransaction {

   void executeTransaction(CacheWrapper cacheWrapper) throws Throwable;

   boolean isReadOnly();
}

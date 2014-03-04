package org.radargun.stages.tpcc.transaction;

import org.radargun.traits.BasicOperations;

/**
 * @author peluso@gsd.inesc-id.pt , peluso@dis.uniroma1.it
 */
public interface TpccTransaction {

   void executeTransaction(BasicOperations.Cache basicCache) throws Throwable;

   boolean isReadOnly();
}

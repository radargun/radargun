package org.radargun.stages.tpcc.transaction;

import org.radargun.traits.BasicOperations;

/**
 * @author Sebastiano Peluso &lt;peluso@gsd.inesc-id.pt, peluso@dis.uniroma1.it&gt;
 */
public interface TpccTransaction {

   void executeTransaction(BasicOperations.Cache basicCache) throws Throwable;

   boolean isReadOnly();
}

package org.radargun.service;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

/**
 * When enlisted within a transaction, the TM will not run the 1phase commit optimisation.
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
class DummyXAResource implements XAResource {
   @Override
   public void commit(Xid xid, boolean b) throws XAException {
      // TODO: Customise this generated block
   }

   @Override
   public void end(Xid xid, int i) throws XAException {
      // TODO: Customise this generated block
   }

   @Override
   public void forget(Xid xid) throws XAException {
      // TODO: Customise this generated block
   }

   @Override
   public int getTransactionTimeout() throws XAException {
      return 0;  // TODO: Customise this generated block
   }

   @Override
   public boolean isSameRM(XAResource xaResource) throws XAException {
      return false;  // TODO: Customise this generated block
   }

   @Override
   public int prepare(Xid xid) throws XAException {
      return 0;  // TODO: Customise this generated block
   }

   @Override
   public Xid[] recover(int i) throws XAException {
      return new Xid[0];  // TODO: Customise this generated block
   }

   @Override
   public void rollback(Xid xid) throws XAException {
      // TODO: Customise this generated block
   }

   @Override
   public boolean setTransactionTimeout(int i) throws XAException {
      return false;  // TODO: Customise this generated block
   }

   @Override
   public void start(Xid xid, int i) throws XAException {
      // TODO: Customise this generated block
   }
}

package org.radargun.stages.cache.stresstest;

import java.util.concurrent.CountDownLatch;

/**
 * Synchronizes alternation of one master and many slaves. First master phase must start before any slave phase.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @since 2/22/13
 */
public class PhaseSynchronizer {
   boolean masterTurn = true;
   int maxSlaves = 0;
   CountDownLatch allSlavesOut;
   CountDownLatch allSlavesIn;
   volatile boolean interrupted = false;

   public void masterPhaseStart() throws InterruptedException {
      if (interrupted) throw new InterruptedException();
      try {
         if (allSlavesOut != null) allSlavesOut.await();
      } catch (InterruptedException e) {
         interrupted = true;
         throw e;
      }
      //System.err.println("Master start");
   }

   public synchronized void masterPhaseEnd() {
      allSlavesIn = new CountDownLatch(maxSlaves);
      allSlavesOut = new CountDownLatch(maxSlaves);
      masterTurn = false;
      this.notifyAll();
      //System.err.println("Master end");
   }

   public void slavePhaseStart() throws InterruptedException {
      if (interrupted) throw new InterruptedException();
      synchronized (this) {
         try {
            while (masterTurn) {
               //System.err.println("Slave " + Thread.currentThread().getName() + " wait");
               this.wait();
            }
         } catch (InterruptedException e) {
            interrupted = true;
            throw e;
         }
         allSlavesIn.countDown();
      }
      //System.err.println("Slave " + Thread.currentThread().getName() + " barrier");
      allSlavesIn.await();
   }

   public void slavePhaseEnd() throws InterruptedException {
      CountDownLatch allOut;
      synchronized (this) {
         allOut = allSlavesOut;
         allOut.countDown();
         if (allOut.getCount() == 0) {
            masterTurn = true;
         }
      }
      //System.err.println("Slave " + Thread.currentThread().getName() + " end");
      try {
         allOut.await();
      } catch (InterruptedException e) {
         interrupted = true;
         throw e;
      }
   }

   public synchronized void setSlaveCount(int count) {
      if (!masterTurn) throw new IllegalStateException();
      maxSlaves = count;
   }
}

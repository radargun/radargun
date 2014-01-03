package org.radargun.cachewrappers;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.infinispan.remoting.transport.Address;

/**
 * // TODO: Document this
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanLifecycle {
   enum State {
      STOPPED,
      STARTING,
      STARTED,
      STOPPING,
      FAILED
   }

   protected final Log log = LogFactory.getLog(getClass());
   protected final boolean trace = log.isTraceEnabled();

   protected final InfinispanWrapper wrapper;

   protected volatile State state = State.STOPPED;
   protected ReentrantLock stateLock = new ReentrantLock();
   protected Thread startingThread;


   public InfinispanLifecycle(InfinispanWrapper wrapper) {
      this.wrapper = wrapper;
   }

   public void setUp() throws Exception {
      try {
         if (beginStart()) {

            wrapper.setUpCaches();
            wrapper.setUpTransactionManager();

            stateLock.lock();
            state = State.STARTED;
            startingThread = null;
            stateLock.unlock();

            postSetUpInternal();
         }
      } catch (Exception e) {
         log.error("Wrapper start failed.", e);
         if (!stateLock.isHeldByCurrentThread()) {
            stateLock.lock();
         }
         state = State.FAILED;
         startingThread = null;
         throw e;
      } finally {
         if (stateLock.isHeldByCurrentThread()) {
            stateLock.unlock();
         }
      }
   }

   protected void postSetUpInternal() throws Exception {
      wrapper.waitForRehash();
   }

   public void tearDown() throws Exception {
      try {
         if (beginStop(false)) {
            List<Address> addressList = wrapper.getCacheManager().getMembers();
            wrapper.getCacheManager().stop();
            log.info("Stopped, previous view is " + addressList);

            stateLock.lock();
            state = State.STOPPED;
         }
      } catch (Exception e) {
         log.error("Wrapper tear down failed.");
         if (!stateLock.isHeldByCurrentThread()) {
            stateLock.lock();
         }
         state = State.FAILED;
         throw e;
      } finally {
         if (stateLock.isHeldByCurrentThread()) {
            stateLock.unlock();
         }
      }
   }

   protected boolean beginStart() throws InterruptedException {
      try {
         stateLock.lock();
         while (state == State.STOPPING) {
            stateLock.unlock();
            log.info("Waiting for the wrapper to stop");
            Thread.sleep(1000);
            stateLock.lock();
         }
         if (state == State.FAILED){
            log.info("Cannot start, previous attempt failed");
         } else if (state == State.STARTING) {
            log.info("Wrapper already starting");
         } else if (state == State.STARTED) {
            log.info("Wrapper already started");
         } else if (state == State.STOPPED) {
            state = State.STARTING;
            startingThread = Thread.currentThread();
            return true;
         }
         return false;
      } finally {
         if (stateLock.isHeldByCurrentThread()) {
            stateLock.unlock();
         }
      }
   }

   protected boolean beginStop(boolean interrupt) throws InterruptedException {
      try {
         stateLock.lock();
         if (interrupt && startingThread != null) {
            log.info("Interrupting the starting thread");
            startingThread.interrupt();
         }
         while (state == State.STARTING) {
            stateLock.unlock();
            log.info("Waiting for the wrapper to start");
            Thread.sleep(1000);
            stateLock.lock();
         }
         if (state == State.FAILED) {
            log.info("Cannot stop, previous attempt failed.");
         } else if (state == State.STOPPING) {
            log.warn("Wrapper already stopping");
         } else if (state == State.STOPPED) {
            log.warn("Wrapper already stopped");
         } else if (state == State.STARTED) {
            state = State.STOPPING;
            return true;
         }
         return false;
      } finally {
         if (stateLock.isHeldByCurrentThread()) {
            stateLock.unlock();
         }
      }
   }

   public boolean isRunning() {
      try {
         stateLock.lock();
         return state == State.STARTED;
      } finally {
         if (stateLock.isHeldByCurrentThread()) {
            stateLock.unlock();
         }
      }
   }
}

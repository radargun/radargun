package org.radargun.service;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.infinispan.remoting.transport.Address;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Lifecycle;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanLifecycle implements Lifecycle {
   enum State {
      STOPPED,
      STARTING,
      STARTED,
      STOPPING,
      FAILED
   }

   protected final Log log = LogFactory.getLog(getClass());
   protected final boolean trace = log.isTraceEnabled();

   protected final InfinispanEmbeddedService service;

   protected volatile State state = State.STOPPED;
   protected ReentrantLock stateLock = new ReentrantLock();
   protected Thread startingThread;


   public InfinispanLifecycle(InfinispanEmbeddedService service) {
      this.service = service;
   }

   @Override
   public void start() {
      log.info("Infinispan version: " + org.infinispan.Version.printVersion());
      log.debug("Loading JGroups from: " + org.jgroups.Version.class.getProtectionDomain().getCodeSource().getLocation());
      log.info("JGroups version: " + org.jgroups.Version.printDescription());
      try {
         if (beginStart()) {

            service.startCaches();

            stateLock.lock();
            state = State.STARTED;
            startingThread = null;
            stateLock.unlock();

            postSetUpInternal();
         }
      } catch (Exception e) {
         log.error("Service start failed.", e);
         if (!stateLock.isHeldByCurrentThread()) {
            stateLock.lock();
         }
         state = State.FAILED;
         startingThread = null;
         throw new RuntimeException(e);
      } finally {
         if (stateLock.isHeldByCurrentThread()) {
            stateLock.unlock();
         }
      }
   }

   protected void postSetUpInternal() throws Exception {
      service.waitForRehash();
   }

   @Override
   public void stop() {
      try {
         if (beginStop(false)) {
            List<Address> addressList = service.cacheManager.getMembers();
            service.stopCaches();
            log.info("Stopped, previous view is " + addressList);

            stateLock.lock();
            state = State.STOPPED;
         }
      } catch (Exception e) {
         log.error("Service stop failed.");
         if (!stateLock.isHeldByCurrentThread()) {
            stateLock.lock();
         }
         state = State.FAILED;
         throw new RuntimeException(e);
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
            log.info("Waiting for the service to stop");
            Thread.sleep(1000);
            stateLock.lock();
         }
         if (state == State.FAILED){
            log.info("Cannot start, previous attempt failed");
         } else if (state == State.STARTING) {
            log.info("Service already starting");
         } else if (state == State.STARTED) {
            log.info("Service already started");
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
            log.info("Waiting for the service to start");
            Thread.sleep(1000);
            stateLock.lock();
         }
         if (state == State.FAILED) {
            log.info("Cannot stop, previous attempt failed.");
         } else if (state == State.STOPPING) {
            log.warn("Service already stopping");
         } else if (state == State.STOPPED) {
            log.warn("Service already stopped");
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

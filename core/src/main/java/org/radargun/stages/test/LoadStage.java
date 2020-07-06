package org.radargun.stages.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.utils.TimeConverter;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Manages thread for data loading")
public abstract class LoadStage extends AbstractDistStage {
   @Property(doc = "Number of loaded entries after which a log entry should be written. Default is 10000.")
   protected long logPeriod = 10000;

   @Property(doc = "The number of threads that should load the entries on one worker. Default is 10.")
   protected int numThreads = 10;

   @Property(doc = "Seed used for initialization of random generators - with same seed (and other arguments)," +
      " the stage guarantees same entries added to the cache. By default the seed is not set.")
   protected Long seed;

   @Property(doc = "During loading phase, if the insert fails, try it again. This is the maximum number of attempts. Default is 10.")
   protected int maxLoadAttempts = 10;

   @Property(doc = "When an attempt to load an entry fails, wait this period to reduce the chances of failing again. Default is one second.",
      converter = TimeConverter.class)
   protected long waitOnError = 1000;

   protected AtomicLong entryCounter = new AtomicLong(0);
   protected AtomicLong sizeSum = new AtomicLong(0);

   protected void logLoaded(long entries, long size, boolean remove) {
      long prevEntryCount, currentEntryCount;
      do {
         prevEntryCount = entryCounter.get();
         currentEntryCount = prevEntryCount + entries;
      } while (!entryCounter.compareAndSet(prevEntryCount, currentEntryCount));
      // just for logs - don't worry about those two not in sync
      long totalSize = sizeSum.addAndGet(size);
      if (prevEntryCount / logPeriod < currentEntryCount / logPeriod) {
         log.infof("This node %s %d entries (~%d bytes)",
            remove ? "removed" : "loaded", currentEntryCount, totalSize);
      }
   }

   protected List<Loader> startLoaders() {
      int threadBase = getExecutingWorkerIndex() * numThreads;
      List<Loader> loaders = new ArrayList<>();
      for (int i = 0; i < numThreads; ++i) {
         Loader loader = createLoader(threadBase, i);
         loaders.add(loader);
         loader.start(); // no special synchronization needed
      }
      return loaders;
   }

   public DistStageAck executeOnWorker() {
      if (!isServiceRunning()) {
         log.info("Not running test on this worker as service is not running.");
         return successfulResponse();
      }
      prepare();
      List<Loader> loaders = startLoaders();
      try {
         stopLoaders(loaders);
      } catch (InterruptedException e) {
         return errorResponse("Interrupted when waiting for the loader to finish");
      } catch (Exception e) {
         return errorResponse("Loader failed with exception", e);
      }
      destroy();
      return successfulResponse();
   }

   /**
    * To be overridden in inheritors.
    */
   protected void prepare() {
   }

   /**
    * To be overridden in inheritors.
    */
   protected void destroy() {
   }

   protected abstract Loader createLoader(int threadBase, int threadIndex);

   protected void stopLoaders(List<Loader> loaders) throws Exception {
      for (Loader loader : loaders) {
         loader.join();
         if (loader.getException() != null) {
            throw loader.getException();
         }
      }
   }

   protected abstract class Loader extends Thread {
      protected final Random random;
      protected final int threadIndex;
      protected Throwable throwable;

      protected Loader(int index) {
         super("Loader-" + index);
         threadIndex = workerState.getWorkerIndex() * numThreads + index;
         random = seed == null ? new Random() : new Random(seed + threadIndex);
      }

      public Exception getException() {
         return throwable == null ? null : new ExecutionException(throwable);
      }

      @Override
      public void run() {
         try {
            for (; ; ) {
               if (!loadDataUnit()) return;
            }
         } catch (Throwable t) {
            log.error("Exception in Loader", t);
            throwable = t;
         }
      }

      protected abstract boolean loadDataUnit();
   }
}

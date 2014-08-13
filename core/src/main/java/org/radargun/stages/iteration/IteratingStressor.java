package org.radargun.stages.iteration;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stats.Statistics;
import org.radargun.traits.Iterable;

/**
 * Stressor thread used in {@link org.radargun.stages.iteration.IterateStage}
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class IteratingStressor extends Thread {
   private final static Log log = LogFactory.getLog(IteratingStressor.class);

   private final Iterable iterable;
   private final String containerName;
   private final Iterable.Filter filter;
   private final Iterable.Converter converter;
   private final int numLoops;
   private final int maxNextFailures;
   private final CountDownLatch startLatch;
   private final Statistics stats;

   private volatile boolean terminate;
   private volatile boolean failed;
   private volatile long minElements = -1;
   private volatile long maxElements = -1;

   public IteratingStressor(int id, Iterable iterable, String containerName,
                            Iterable.Filter filter, Iterable.Converter converter,
                            int maxNextFailures,
                            int numLoops, CountDownLatch startLatch, Statistics stats) {
      super("Stressor-" + id);
      this.iterable = iterable;
      this.containerName = containerName;
      this.filter = filter;
      this.converter = converter;
      this.numLoops = numLoops;
      this.maxNextFailures = maxNextFailures;
      this.startLatch = startLatch;
      this.stats = stats;
   }

   @Override
   public void run() {
      try {
         startLatch.await();
      } catch (InterruptedException e) {
         log.warn("Interrupted while waiting for start", e);
      }
      if (terminate) return;
      stats.begin();
      try {
         for (int i = 0; i < numLoops; ++i) {
            if (iterateLoop()) return;
         }
      } finally {
         stats.end();
      }
   }

   private boolean iterateLoop() {
      Iterable.CloseableIterator iterator;
      long getIteratorStart = System.nanoTime();
      try {
         if (converter == null) {
            iterator = iterable.getIterator(containerName, filter);
         } else {
            iterator = iterable.getIterator(containerName, filter, converter);
         }
         stats.registerRequest(System.nanoTime() - getIteratorStart, Iterable.GET_ITERATOR);
      } catch (Exception e) {
         log.error("Failed to get the iterator", e);
         this.failed = true;
         return true;
      }
      int nextFailures = 0;
      long elements = 0;
      long loopStart = System.nanoTime();
      while (!terminate) {
         boolean hasNext;
         long hasNextStart = System.nanoTime();
         try {
            hasNext = iterator.hasNext();
            stats.registerRequest(System.nanoTime() - hasNextStart, Iterable.HAS_NEXT);
         } catch (Exception e) {
            stats.registerError(System.nanoTime() - hasNextStart, Iterable.HAS_NEXT);
            hasNext = false;
            log.error("hasNext() failed", e);
         }
         if (!hasNext) break;
         long nextStart = System.nanoTime();
         try {
            iterator.next();
            stats.registerRequest(System.nanoTime() - nextStart, Iterable.NEXT);
            elements++;
         } catch (Exception e) {
            stats.registerError(System.nanoTime() - nextStart, Iterable.NEXT);
            log.error("next() failed", e);
            nextFailures++;
            if (nextFailures > maxNextFailures) {
               failed = true;
               break;
            }
         }
      }
      if (minElements < 0 || elements < minElements) minElements = elements;
      if (maxElements < 0 || elements > maxElements) maxElements = elements;
      try {
         iterator.close();
      } catch (IOException e) {
         log.error("Failed to close the iterator", e);
         failed = true;
      }
      stats.registerRequest(System.nanoTime() - loopStart, Iterable.FULL_LOOP);

      return false;
   }

   public void terminate() {
      terminate = true;
   }

   public boolean isFailed() {
      return failed;
   }

   public long getMinElements() {
      return minElements;
   }

   public long getMaxElements() {
      return maxElements;
   }

   public Statistics getStats() {
      return stats;
   }
}

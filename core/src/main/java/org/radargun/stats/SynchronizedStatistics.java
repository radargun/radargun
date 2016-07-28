package org.radargun.stats;

import org.radargun.Operation;
import org.radargun.config.DefinitionElement;

/**
 * Wrapper over DefaultStatistics that provides synchronized access and sealing.
 */
@DefinitionElement(name = "synchronized", doc = "Basic statistics that support concurrent access.")
public class SynchronizedStatistics extends BasicStatistics {

   protected boolean snapshot = false;

   public SynchronizedStatistics(OperationStats prototype) {
      super(prototype);
   }

   @Override
   public SynchronizedStatistics newInstance() {
      return new SynchronizedStatistics(prototype);
   }

   public boolean isSnapshot() {
      return snapshot;
   }

   protected void ensureSnapshot() {
      if (!snapshot) {
         throw new RuntimeException("this operation can be performed only on snapshot");
      }
   }

   protected void ensureNotSnapshot() {
      if (snapshot) {
         throw new RuntimeException("this operation cannot be performed on snapshot");
      }
   }

   public synchronized SynchronizedStatistics snapshot(boolean reset) {
      ensureNotSnapshot();
      SynchronizedStatistics copy = copy();
      copy.end();
      copy.snapshot = true;
      if (reset) {
         reset();
      }
      return copy;
   }

   @Override
   public synchronized void record(Request request, Operation operation) {
      ensureNotSnapshot();
      super.record(request, operation);
   }

   @Override
   public synchronized void record(Message message, Operation operation) {
      ensureNotSnapshot();
      super.record(message, operation);
   }

   @Override
   public synchronized void reset() {
      ensureNotSnapshot();
      super.reset();
   }

   @Override
   public synchronized SynchronizedStatistics copy() {
      SynchronizedStatistics copy = (SynchronizedStatistics) super.copy();
      copy.snapshot = snapshot;
      return copy;
   }

   @Override
   public synchronized void merge(Statistics otherStats) {
      super.merge(otherStats);
   }

   @Override
   public synchronized long getBegin() {
      return super.getBegin();
   }

   @Override
   public synchronized long getEnd() {
      return super.getEnd();
   }
}

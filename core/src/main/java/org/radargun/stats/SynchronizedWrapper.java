package org.radargun.stats;

import java.util.Map;
import java.util.Set;

import org.radargun.Operation;

public class SynchronizedWrapper implements Statistics {
   private final Statistics delegate;

   public SynchronizedWrapper(Statistics delegate) {
      this.delegate = delegate;
   }

   @Override
   public synchronized void registerOperationsGroup(String name, Set<Operation> operations) {
      delegate.registerOperationsGroup(name, operations);
   }

   @Override
   public synchronized String getOperationsGroup(Operation operation) {
      return delegate.getOperationsGroup(operation);
   }

   @Override
   public synchronized Map<String, Set<Operation>> getGroupOperationsMap() {
      return delegate.getGroupOperationsMap();
   }

   @Override
   public synchronized Map<String, OperationStats> getOperationStatsForGroups() {
      return delegate.getOperationStatsForGroups();
   }

   @Override
   public synchronized Map<String, OperationStats> getOperationsStats() {
      return delegate.getOperationsStats();
   }

   @Override
   public synchronized void begin() {
      delegate.begin();
   }

   @Override
   public synchronized void end() {
      delegate.end();
   }

   @Override
   public synchronized void reset() {
      delegate.reset();
   }

   @Override
   public Request startRequest() {
      return new Request(this);
   }

   @Override
   public Message message() {
      return new Message(this);
   }

   @Override
   public RequestSet requestSet() {
      return new RequestSet(this);
   }

   @Override
   public synchronized void record(Request request, Operation operation) {
      delegate.record(request, operation);
   }

   @Override
   public synchronized void record(Message message, Operation operation) {
      delegate.record(message, operation);
   }

   @Override
   public synchronized void record(RequestSet requestSet, Operation operation) {
      delegate.record(requestSet, operation);
   }

   @Override
   public synchronized void discard(Request request) {
      delegate.discard(request);
   }

   @Override
   public synchronized void discard(Message request) {
      delegate.discard(request);
   }

   @Override
   public synchronized void discard(RequestSet request) {
      delegate.discard(request);
   }

   @Override
   public synchronized Statistics newInstance() {
      return new SynchronizedWrapper(delegate.newInstance());
   }

   @Override
   public synchronized Statistics copy() {
      return new SynchronizedWrapper(delegate.copy());
   }

   @Override
   public void merge(Statistics otherStats) {
      if (otherStats instanceof SynchronizedWrapper) {
         synchronized (otherStats) {
            otherStats = ((SynchronizedWrapper) otherStats).delegate.copy();
         }
      }
      synchronized (this) {
         delegate.merge(otherStats);
      }
   }

   @Override
   public synchronized long getBegin() {
      return delegate.getBegin();
   }

   @Override
   public synchronized long getEnd() {
      return delegate.getEnd();
   }

   @Override
   public synchronized Set<String> getOperations() {
      return delegate.getOperations();
   }

   @Override
   public synchronized OperationStats getOperationStats(String operation) {
      return delegate.getOperationStats(operation);
   }

   @Override
   public synchronized <T> T getRepresentation(String operation, Class<T> clazz, Object... args) {
      return delegate.getRepresentation(operation, clazz, args);
   }

   @Override
   public boolean isThreadSafe() {
      return true;
   }
}

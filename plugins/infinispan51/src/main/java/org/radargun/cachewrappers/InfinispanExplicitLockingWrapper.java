/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.radargun.cachewrappers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import javax.transaction.Status;
import org.infinispan.Cache;
import org.infinispan.transaction.LockingMode;
import org.radargun.features.BulkOperationsCapable;
import org.radargun.utils.TypedProperties;

public class InfinispanExplicitLockingWrapper extends InfinispanKeyAffinityWrapper implements BulkOperationsCapable {

   private boolean isExplicitLocking;
   
   @Override
   protected void postSetUpInternal(TypedProperties confAttributes) throws Exception {
      super.postSetUpInternal(confAttributes);
      setUpExplicitLocking(getCache(null), confAttributes);
   }
   
   protected void setUpExplicitLocking(Cache<Object, Object> cache, TypedProperties confAttributes) {
      LockingMode lockingMode = cache.getAdvancedCache().getCacheConfiguration().transaction()
               .lockingMode();

      Object explicitLocking = confAttributes.get("explicitLocking");
      if (explicitLocking != null && explicitLocking.equals("true")
               && lockingMode.equals(LockingMode.PESSIMISTIC)) {
         isExplicitLocking = true;
         log.info("Using explicit locking!");
      }
   }

   /**
    * If transactions and explicit locking are enabled and the locking mode is pessimistic then
    * explicit locking is performed! i.e. before any put the key is explicitly locked by call
    * cache.lock(key). Doesn't lock the key if the request was made by ClusterValidationStage.
    */
   @Override
   public void put(String bucket, Object key, Object value) throws Exception {
      boolean shouldStopTransactionHere = false;
      if (isExplicitLocking && !isClusterValidationRequest(bucket)) {
         if (tm.getStatus() == Status.STATUS_NO_TRANSACTION) {
            shouldStopTransactionHere = true;
            startTransaction();
         }
         getCache(bucket).getAdvancedCache().lock(key);
      }
      super.put(bucket, key, value);
      if (shouldStopTransactionHere) {
         endTransaction(true);
      }
   }
   
   @Override
   public Object remove(String bucket, Object key) throws Exception {
      boolean shouldStopTransactionHere = false;
      if (isExplicitLocking && !isClusterValidationRequest(bucket)) {
         if (tm.getStatus() == Status.STATUS_NO_TRANSACTION) {
            shouldStopTransactionHere = true;
            startTransaction();
         }
         getCache(bucket).getAdvancedCache().lock(key);
      }
      Object old = super.remove(bucket, key);
      if (shouldStopTransactionHere) {
         endTransaction(true);
      }
      return old;
   }

   @Override
   public boolean replace(String bucket, Object key, Object oldValue, Object newValue) throws Exception {
      boolean shouldStopTransactionHere = false;
      if (isExplicitLocking && !isClusterValidationRequest(bucket)) {
         if (tm.getStatus() == Status.STATUS_NO_TRANSACTION) {
            shouldStopTransactionHere = true;
            startTransaction();
         }
         getCache(bucket).getAdvancedCache().lock(key);
      }
      boolean replaced = super.replace(bucket, key, oldValue, newValue);
      if (shouldStopTransactionHere) {
         endTransaction(true);
      }
      return replaced;
   }

   @Override
   public Object putIfAbsent(String bucket, Object key, Object value) throws Exception {
      boolean shouldStopTransactionHere = false;
      if (isExplicitLocking && !isClusterValidationRequest(bucket)) {
         if (tm.getStatus() == Status.STATUS_NO_TRANSACTION) {
            shouldStopTransactionHere = true;
            startTransaction();
         }
         getCache(bucket).getAdvancedCache().lock(key);
      }
      Object old = super.putIfAbsent(bucket, key, value);
      if (shouldStopTransactionHere) {
         endTransaction(true);
      }
      return old;
   }

   protected boolean isClusterValidationRequest(String bucket) {
      return bucket.startsWith("clusterValidation") ? true : false;
   }

   public boolean isExplicitLockingEnabled() {
      return isExplicitLocking;
   }

   @Override
   public Collection<Object> getAll(String bucket, Collection<Object> keys) throws Exception {
      if (trace) {
         StringBuilder sb = new StringBuilder("GET_ALL ");
         for (Object key : keys) {
            sb.append(key).append(", ");
         }
      }
      Cache<Object, Object> cache = getCache(bucket);
      List<Future<Object>> futures = new ArrayList<Future<Object>>(keys.size());
      List<Object> values = new ArrayList<Object>(keys.size());
      for (Object key : keys) {
         futures.add(cache.getAsync(key));
      }
      for (Future<Object> future : futures) {
         values.add(future.get());
      }
      return values;
   }

   @Override
   public void putAll(String bucket, Map<Object, Object> entries) throws Exception {
      if (trace) {
         StringBuilder sb = new StringBuilder("PUT_ALL ");
         for (Object key : entries.keySet()) {
            sb.append(key).append(", ");
         }
      }
      Cache<Object, Object> cache = getCache(bucket);
      boolean shouldStopTransactionHere = false;
      if (isExplicitLocking && !isClusterValidationRequest(bucket)) {
         if (tm.getStatus() == Status.STATUS_NO_TRANSACTION) {
            shouldStopTransactionHere = true;
            startTransaction();
         }
         cache.getAdvancedCache().lock(entries.keySet());
      }
      cache.putAll(entries);
      if (shouldStopTransactionHere) {
         endTransaction(true);
      }
   }

   @Override
   public Collection<Object> removeAll(String bucket, Collection<Object> keys) throws Exception {
      if (trace) {
         StringBuilder sb = new StringBuilder("GET_ALL ");
         for (Object key : keys) {
            sb.append(key).append(", ");
         }
      }
      Cache<Object, Object> cache = getCache(bucket);
      boolean shouldStopTransactionHere = false;
      if (isExplicitLocking && !isClusterValidationRequest(bucket)) {
         if (tm.getStatus() == Status.STATUS_NO_TRANSACTION) {
            shouldStopTransactionHere = true;
            startTransaction();
         }
         cache.getAdvancedCache().lock(keys);
      }
      List<Future<Object>> futures = new ArrayList<Future<Object>>(keys.size());
      List<Object> values = new ArrayList<Object>(keys.size());
      for (Object key : keys) {
         futures.add(cache.removeAsync(key));
      }
      for (Future<Object> future : futures) {
         values.add(future.get());
      }
      if (shouldStopTransactionHere) {
         endTransaction(true);
      }
      return values;
   }
}

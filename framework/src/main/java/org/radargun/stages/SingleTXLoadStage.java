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
package org.radargun.stages;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.config.TimeConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Performs single transaction in multiple threads on multiple slaves.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Performs single transaction with requests on key0 .. keyN in multiple threads on multiple slaves.")
public class SingleTXLoadStage extends AbstractDistStage {

   @Property(converter = TimeConverter.class, doc = "The enforced duration of the transaction. If > 0 the threads " +
         "will sleep for duration/transactionSize after each request. Default is 0.")
   private long duration = 0;

   @Property(doc = "Number of threads that should execute the transaction. Default is 1.")
   private int threads = 1;

   @Property(doc = "Indices of slaves which should commit the transaction (others will rollback). Default is all commit.")
   private Set<Integer> commitSlave; // null == all commit

   @Property(doc = "Indices of threads which should commit the transaction (others will rollback). Default is all commit.")
   private Set<Integer> commitThread; // null == all commit

   @Property(doc = "Number of request in the transaction. Default is 20.")
   private int transactionSize = 20;

   @Property(doc = "The threads by default do the PUT request, if this is set to true they will do REMOVE. Default is false.")
   private boolean delete;
   
   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      if (slaves != null && !slaves.contains(slaveIndex)) {
         return ack;
      }
      List<ClientThread> clients = new ArrayList<ClientThread>();
      for (int i = 0; i < threads; ++i) {
         ClientThread ct = new ClientThread(i);
         clients.add(ct);
         ct.start();
      }
      for (ClientThread ct : clients) {
         try {
            ct.join();
            if (ct.exception != null) {
               exception(ack, "Exception in client thread", ct.exception);
            }
         } catch (InterruptedException e) {
            exception(ack, "Failed to join " + ct, ct.exception);
         }
      }
      return ack;
   }

   private void exception(DefaultDistStageAck ack, String message, Exception e) {
      log.error(message, e);
      ack.setError(true);
      ack.setErrorMessage(message);
      if (e != null) {
         ack.setRemoteException(e);
      }
   }

   private class ClientThread extends Thread {
      private int id;
      public Exception exception;
      
      public ClientThread(int id) {
         super("ClientThread-" + slaveIndex + "-" + id);
         this.id = id;
      }
      
      @Override
      public void run() {
         try {
            CacheWrapper cacheWrapper = slaveState.getCacheWrapper();
            log.trace("Beginning transaction");
            cacheWrapper.startTransaction();
            for (int i = 0; i < transactionSize; ++i) {
               if (!delete) {
                  try {
                	 log.trace("Inserting key");
                     cacheWrapper.put(null, "txKey" + i, "txValue" + i + "@" + slaveIndex + "-" + id);
                     log.trace("Key inserted");
                  } catch (Exception e) {
                     log.error("Failed to insert key txKey" + i, e);
                     throw e;
                  }
               } else {
                  try {
                  cacheWrapper.remove(null, "txKey" + i);
                  } catch (Exception e) {
                     log.error("Failed to remove key txKey" + i, e);
                     throw e;
                  }
               }
               if (duration > 0) {
                  try {
                	 log.trace("Sleeping for " + (duration / transactionSize));
                     Thread.sleep(duration / transactionSize);
                  } catch (InterruptedException e) {
                  }
               }            
            }
            boolean successfull = (commitSlave == null || commitSlave.contains(slaveIndex)) && (commitThread == null || commitThread.contains(id));
            cacheWrapper.endTransaction(successfull);
            if (successfull) log.trace("Committed transaction");
            else log.debug("Rolled back transaction");
         } catch (Exception e) {
            exception = e;
         }
      }
   }
}

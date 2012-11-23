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
import org.radargun.stages.helpers.ParseHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SingleTXLoadStage extends AbstractDistStage {

   private long duration = 0;
   private int threads = 1;
   private Set<Integer> commitSlave; // null == all commit
   private Set<Integer> commitThread; // null == all commit
   private int transactionSize = 20;
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
   
   public void setDuration(long duration) {
      this.duration = duration;
   }

   public void setThreads(int threads) {
      this.threads = threads;
   }

   public void setCommitSlave(String commitSlave) {
      this.commitSlave = ParseHelper.parseSet(commitSlave, "commitSlave", log);
   }
   
   public void setCommitThread(String commitThread) {
      this.commitThread = ParseHelper.parseSet(commitThread, "commitThread", log);
   }

   public void setTransactionSize(int transactionSize) {
      this.transactionSize = transactionSize;
   }

   public void setDelete(boolean delete) {
      this.delete = delete;
   }
   
   @Override
   public String toString() {
	   return String.format("SingleTXLoadStage(delete=%s, transactionSize=%d, commitSlave=%s, commitThread=%s, threads=%d, duration=%d, %s",
            delete, transactionSize, ParseHelper.toString(commitSlave, "all"), ParseHelper.toString(commitThread, "all"), threads, duration, super.toString());
   }
}

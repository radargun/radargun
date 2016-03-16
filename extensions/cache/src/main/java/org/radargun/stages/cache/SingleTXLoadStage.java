package org.radargun.stages.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Transactional;
import org.radargun.utils.TimeConverter;

/**
 * Performs single transaction in multiple threads on multiple slaves.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Performs single transaction with requests on key0 .. keyN in multiple threads on multiple slaves.")
public class SingleTXLoadStage extends AbstractDistStage {

   @Property(converter = TimeConverter.class, doc = "The enforced duration of the transaction. If > 0 the threads " +
         "will sleep for duration/transactionSize after each request. Default is 0.")
   public long duration = 0;

   @Property(doc = "Number of threads that should execute the transaction. Default is 1.")
   public int threads = 1;

   @Property(doc = "Indices of slaves which should commit the transaction (others will rollback). Default is all commit.")
   public Set<Integer> commitSlave; // null == all commit

   @Property(doc = "Indices of threads which should commit the transaction (others will rollback). Default is all commit.")
   public Set<Integer> commitThread; // null == all commit

   @Property(doc = "Number of request in the transaction. Default is 20.")
   public int transactionSize = 20;

   @Property(doc = "The threads by default do the PUT request, if this is set to true they will do REMOVE. Default is false.")
   public boolean delete;

   @InjectTrait
   private BasicOperations basicOperations;

   @InjectTrait
   private Transactional transactional;

   @Override
   public DistStageAck executeOnSlave() {
      if (!shouldExecute()) {
         return successfulResponse();
      }
      List<ClientThread> clients = new ArrayList<ClientThread>();
      for (int i = 0; i < threads; ++i) {
         ClientThread ct = new ClientThread(i);
         clients.add(ct);
         ct.start();
      }
      DistStageAck ack = successfulResponse();
      for (ClientThread ct : clients) {
         try {
            ct.join();
            if (ct.exception != null) {
               ack = errorResponse("Exception in client thread", ct.exception);
            }
         } catch (InterruptedException e) {
            ack = errorResponse("Failed to join " + ct, ct.exception);
         }
      }
      return ack;
   }

   private class ClientThread extends Thread {
      private int id;
      public Exception exception;

      public ClientThread(int id) {
         super("ClientThread-" + slaveState.getSlaveIndex() + "-" + id);
         this.id = id;
      }
      
      @Override
      public void run() {
         try {
            Transactional.Transaction tx = transactional.getTransaction();
            BasicOperations.Cache cache = tx.wrap(basicOperations.getCache(null));
            log.trace("Beginning transaction");
            tx.begin();
            for (int i = 0; i < transactionSize; ++i) {
               if (!delete) {
                  try {
                	 log.trace("Inserting key");
                     cache.put("txKey" + i, "txValue" + i + "@" + slaveState.getSlaveIndex() + "-" + id);
                     log.trace("Key inserted");
                  } catch (Exception e) {
                     log.error("Failed to insert key txKey" + i, e);
                     throw e;
                  }
               } else {
                  try {
                  cache.remove("txKey" + i);
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
                     log.error("Thread has been interrupted", e);
                     Thread.currentThread().interrupt();
                  }
               }            
            }
            boolean successfull = (commitSlave == null || commitSlave.contains(slaveState.getSlaveIndex())) && (commitThread == null || commitThread.contains(id));
            if (successfull) {
               tx.commit();
               log.trace("Committed transaction");
            } else {
               tx.rollback();
               log.debug("Rolled back transaction");
            }
         } catch (Exception e) {
            exception = e;
         }
      }
   }
}

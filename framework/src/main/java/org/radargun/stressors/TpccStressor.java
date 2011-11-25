package org.radargun.stressors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.CacheWrapperStressor;
import org.radargun.tpcc.ElementNotFoundException;
import org.radargun.tpcc.TpccTerminal;
import org.radargun.tpcc.TpccTools;
import org.radargun.tpcc.transaction.NewOrderTransaction;
import org.radargun.tpcc.transaction.OrderStatusTransaction;
import org.radargun.tpcc.transaction.PaymentTransaction;
import org.radargun.tpcc.transaction.TpccTransaction;
import org.radargun.utils.Utils;


/**
 * On multiple threads executes implementations of TPC-C Transaction Profiles against the CacheWrapper, and returns the
 * result as a Map.
 *
 * @author peluso@gsd.inesc-id.pt , peluso@dis.uniroma1.it
 */
public class TpccStressor implements CacheWrapperStressor {

   private static Log log = LogFactory.getLog(TpccStressor.class);

   /**
    * the number of threads that will work on this cache wrapper.
    */
   private int numOfThreads = 10;

   /**
    * this node's index in the Radargun cluster.  -1 is used for local benchmarks.
    */
   private int nodeIndex = -1;

   /**
    * the number of nodes in the Radargun cluster.
    */
   private int numSlaves = 0;

   /**
    * total time (in seconds) of simulation for each stressor thread
    */
   private long perThreadSimulTime = 30L;

   /**
    * average arrival rate of the transactions to the system
    */
   private double arrivalRate = 0.0D;

   /**
    * percentage of Payment transactions
    */
   private double paymentWeight = 45.0D;

   /**
    * percentage of Order Status transactions
    */
   private double orderStatusWeight = 5.0D;

   private CacheWrapper cacheWrapper;
   private static Random r = new Random();
   private long startTime;
   private volatile CountDownLatch startPoint;
   private AtomicLong completedThread;
   private BlockingQueue<RequestType> queue;
   private AtomicLong countJobs;
   private Producer[] producers;


   public Map<String, String> stress(CacheWrapper wrapper) {
      this.cacheWrapper = wrapper;

      initializeToolsParameters();

      completedThread = new AtomicLong(0L);

      if (this.arrivalRate != 0.0) {     //Open system
         queue = new ArrayBlockingQueue<RequestType>(7000);
         countJobs = new AtomicLong(0L);
         producers = new Producer[3];
         producers[0] = new Producer(TpccTerminal.NEW_ORDER, 100.0 - (this.paymentWeight + this.orderStatusWeight));
         producers[1] = new Producer(TpccTerminal.PAYMENT, this.paymentWeight);
         producers[2] = new Producer(TpccTerminal.ORDER_STATUS, this.orderStatusWeight);
      }

      startTime = System.currentTimeMillis();
      log.info("Executing: " + this.toString());

      List<Stressor> stressors;
      try {
         if (this.arrivalRate != 0.0) { //Open system
            for (Producer producer : producers) {
               producer.start();
            }
         }
         stressors = executeOperations();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
      return processResults(stressors);
   }

   public void destroy() throws Exception {
      cacheWrapper.empty();
      cacheWrapper = null;
   }

   private void initializeToolsParameters() {


      try {
         TpccTools.C_C_LAST = (Long) cacheWrapper.get(null, "C_C_LAST");

         TpccTools.C_C_ID = (Long) cacheWrapper.get(null, "C_C_ID");

         TpccTools.C_OL_I_ID = (Long) cacheWrapper.get(null, "C_OL_ID");

      } catch (Exception e) {
         log.error("Error", e);
      }

   }

   private Map<String, String> processResults(List<Stressor> stressors) {

      long duration = 0;

      int reads = 0;
      int writes = 0;
      int newOrderTransactions = 0;
      int paymentTransactions = 0;

      int failures = 0;
      int rdFailures = 0;
      int wrFailures = 0;
      int nrWrFailuresOnCommit = 0;
      int newOrderFailures = 0;
      int paymentFailures = 0;
      int appFailures = 0;

      long readsDurations = 0L;
      long writesDurations = 0L;
      long newOrderDurations = 0L;
      long paymentDurations = 0L;
      long successful_writesDurations = 0L;
      long successful_readsDurations = 0L;
      long writeServiceTimes = 0L;
      long readServiceTimes = 0L;
      long newOrderServiceTimes = 0L;
      long paymentServiceTimes = 0L;

      long successful_commitWriteDurations = 0L;
      long aborted_commitWriteDurations = 0L;
      long commitWriteDurations = 0L;

      long writeInQueueTimes = 0L;
      long readInQueueTimes = 0L;
      long newOrderInQueueTimes = 0L;
      long paymentInQueueTimes = 0L;
      long numWritesDequeued = 0L;
      long numReadsDequeued = 0L;
      long numNewOrderDequeued = 0L;
      long numPaymentDequeued = 0L;

      for (Stressor stressor : stressors) {
         duration += stressor.totalDuration(); //in nanosec
         readsDurations += stressor.readDuration; //in nanosec
         writesDurations += stressor.writeDuration; //in nanosec
         newOrderDurations += stressor.newOrderDuration; //in nanosec
         paymentDurations += stressor.paymentDuration; //in nanosec
         successful_writesDurations += stressor.successful_writeDuration; //in nanosec
         successful_readsDurations += stressor.successful_readDuration; //in nanosec

         successful_commitWriteDurations += stressor.successful_commitWriteDuration; //in nanosec
         aborted_commitWriteDurations += stressor.aborted_commitWriteDuration; //in nanosec
         commitWriteDurations += stressor.commitWriteDuration; //in nanosec;

         writeServiceTimes += stressor.writeServiceTime;
         readServiceTimes += stressor.readServiceTime;
         newOrderServiceTimes += stressor.newOrderServiceTime;
         paymentServiceTimes += stressor.paymentServiceTime;

         reads += stressor.reads;
         writes += stressor.writes;
         newOrderTransactions += stressor.newOrder;
         paymentTransactions += stressor.payment;

         failures += stressor.nrFailures;
         rdFailures += stressor.nrRdFailures;
         wrFailures += stressor.nrWrFailures;
         nrWrFailuresOnCommit += stressor.nrWrFailuresOnCommit;
         newOrderFailures += stressor.nrNewOrderFailures;
         paymentFailures += stressor.nrPaymentFailures;
         appFailures += stressor.appFailures;

         writeInQueueTimes += stressor.writeInQueueTime;
         readInQueueTimes += stressor.readInQueueTime;
         newOrderInQueueTimes += stressor.newOrderInQueueTime;
         paymentInQueueTimes += stressor.paymentInQueueTime;
         numWritesDequeued += stressor.numWriteDequeued;
         numReadsDequeued += stressor.numReadDequeued;
         numNewOrderDequeued += stressor.numNewOrderDequeued;
         numPaymentDequeued += stressor.numPaymentDequeued;
      }

      duration = duration / 1000000; // nanosec to millisec
      readsDurations = readsDurations / 1000; //nanosec to microsec
      writesDurations = writesDurations / 1000; //nanosec to microsec
      newOrderDurations = newOrderDurations / 1000; //nanosec to microsec
      paymentDurations = paymentDurations / 1000;//nanosec to microsec
      successful_readsDurations = successful_readsDurations / 1000; //nanosec to microsec
      successful_writesDurations = successful_writesDurations / 1000; //nanosec to microsec
      successful_commitWriteDurations = successful_commitWriteDurations / 1000; //nanosec to microsec
      aborted_commitWriteDurations = aborted_commitWriteDurations / 1000; //nanosec to microsec
      commitWriteDurations = commitWriteDurations / 1000; //nanosec to microsec
      writeServiceTimes = writeServiceTimes / 1000; //nanosec to microsec
      readServiceTimes = readServiceTimes / 1000; //nanosec to microsec
      newOrderServiceTimes = newOrderServiceTimes / 1000; //nanosec to microsec
      paymentServiceTimes = paymentServiceTimes / 1000; //nanosec to microsec

      writeInQueueTimes = writeInQueueTimes / 1000;//nanosec to microsec
      readInQueueTimes = readInQueueTimes / 1000;//nanosec to microsec
      newOrderInQueueTimes = newOrderInQueueTimes / 1000;//nanosec to microsec
      paymentInQueueTimes = paymentInQueueTimes / 1000;//nanosec to microsec

      Map<String, String> results = new LinkedHashMap<String, String>();
      results.put("DURATION (msec)", str((duration / this.numOfThreads)));
      double requestPerSec = (reads + writes) / ((duration / numOfThreads) / 1000.0);
      results.put("REQ_PER_SEC", str(requestPerSec));

      double wrtPerSec = 0;
      double rdPerSec = 0;
      double newOrderPerSec = 0;
      double paymentPerSec = 0;

      if (readsDurations + writesDurations == 0)
         results.put("READS_PER_SEC", str(0));
      else {
         rdPerSec = reads / (((readsDurations + writesDurations) / numOfThreads) / 1000000.0);
         results.put("READS_PER_SEC", str(rdPerSec));
      }

      if (writesDurations + readsDurations == 0)
         results.put("WRITES_PER_SEC", str(0));
      else {
         wrtPerSec = writes / (((writesDurations + readsDurations) / numOfThreads) / 1000000.0);
         results.put("WRITES_PER_SEC", str(wrtPerSec));
      }

      if (writesDurations + readsDurations == 0)
         results.put("NEW_ORDER_PER_SEC", str(0));
      else {
         newOrderPerSec = newOrderTransactions / (((writesDurations + readsDurations) / numOfThreads) / 1000000.0);

         results.put("NEW_ORDER_PER_SEC", str(newOrderPerSec));
      }
      if (writesDurations + readsDurations == 0)
         results.put("PAYMENT_PER_SEC", str(0));
      else {
         paymentPerSec = paymentTransactions / (((writesDurations + readsDurations) / numOfThreads) / 1000000.0);

         results.put("PAYMENT_PER_SEC", str(paymentPerSec));
      }

      results.put("READ_COUNT", str(reads));
      results.put("WRITE_COUNT", str(writes));
      results.put("NEW_ORDER_COUNT", str(newOrderTransactions));
      results.put("PAYMENT_COUNT", str(paymentTransactions));
      results.put("FAILURES", str(failures));
      results.put("APPLICATION_FAILURES", str(appFailures));
      results.put("WRITE_FAILURES", str(wrFailures));
      results.put("NEW_ORDER_FAILURES", str(newOrderFailures));
      results.put("PAYMENT_FAILURES", str(paymentFailures));
      results.put("READ_FAILURES", str(rdFailures));

      if ((reads + writes) != 0)
         results.put("AVG_SUCCESSFUL_DURATION (usec)", str((successful_writesDurations + successful_readsDurations) / (reads + writes)));
      else
         results.put("AVG_SUCCESSFUL_DURATION (usec)", str(0));


      if (reads != 0)
         results.put("AVG_SUCCESSFUL_READ_DURATION (usec)", str(successful_readsDurations / reads));
      else
         results.put("AVG_SUCCESSFUL_READ_DURATION (usec)", str(0));


      if (writes != 0)
         results.put("AVG_SUCCESSFUL_WRITE_DURATION (usec)", str(successful_writesDurations / writes));
      else
         results.put("AVG_SUCCESSFUL_WRITE_DURATION (usec)", str(0));


      if (writes != 0) {
         results.put("AVG_SUCCESSFUL_COMMIT_WRITE_DURATION (usec)", str((successful_commitWriteDurations / writes)));
      } else {
         results.put("AVG_SUCCESSFUL_COMMIT_WRITE_DURATION (usec)", str(0));
      }

      if (nrWrFailuresOnCommit != 0) {
         results.put("AVG_ABORTED_COMMIT_WRITE_DURATION (usec)", str((aborted_commitWriteDurations / nrWrFailuresOnCommit)));
      } else {
         results.put("AVG_ABORTED_COMMIT_WRITE_DURATION (usec)", str(0));
      }


      if (writes + nrWrFailuresOnCommit != 0) {
         results.put("AVG_COMMIT_WRITE_DURATION (usec)", str((commitWriteDurations / (writes + nrWrFailuresOnCommit))));
      } else {
         results.put("AVG_COMMIT_WRITE_DURATION (usec)", str(0));
      }

      if ((reads + rdFailures) != 0)
         results.put("AVG_RD_SERVICE_TIME (usec)", str(readServiceTimes / (reads + rdFailures)));
      else
         results.put("AVG_RD_SERVICE_TIME (usec)", str(0));

      if ((writes + wrFailures) != 0)
         results.put("AVG_WR_SERVICE_TIME (usec)", str(writeServiceTimes / (writes + wrFailures)));
      else
         results.put("AVG_WR_SERVICE_TIME (usec)", str(0));

      if ((newOrderTransactions + newOrderFailures) != 0)
         results.put("AVG_NEW_ORDER_SERVICE_TIME (usec)", str(newOrderServiceTimes / (newOrderTransactions + newOrderFailures)));
      else
         results.put("AVG_NEW_ORDER_SERVICE_TIME (usec)", str(0));

      if ((paymentTransactions + paymentFailures) != 0)
         results.put("AVG_PAYMENT_SERVICE_TIME (usec)", str(paymentServiceTimes / (paymentTransactions + paymentFailures)));
      else
         results.put("AVG_PAYMENT_SERVICE_TIME (usec)", str(0));

      if (numWritesDequeued != 0)
         results.put("AVG_WR_INQUEUE_TIME (usec)", str(writeInQueueTimes / numWritesDequeued));
      else
         results.put("AVG_WR_INQUEUE_TIME (usec)", str(0));
      if (numReadsDequeued != 0)
         results.put("AVG_RD_INQUEUE_TIME (usec)", str(readInQueueTimes / numReadsDequeued));
      else
         results.put("AVG_RD_INQUEUE_TIME (usec)", str(0));
      if (numNewOrderDequeued != 0)
         results.put("AVG_NEW_ORDER_INQUEUE_TIME (usec)", str(newOrderInQueueTimes / numNewOrderDequeued));
      else
         results.put("AVG_NEW_ORDER_INQUEUE_TIME (usec)", str(0));
      if (numPaymentDequeued != 0)
         results.put("AVG_PAYMENT_INQUEUE_TIME (usec)", str(paymentInQueueTimes / numPaymentDequeued));
      else
         results.put("AVG_PAYMENT_INQUEUE_TIME (usec)", str(0));


      log.info("Finished generating report. Nr of failed operations on this node is: " + failures +
                     ". Test duration is: " + Utils.getDurationString(System.currentTimeMillis() - startTime));
      return results;
   }

   private List<Stressor> executeOperations() throws Exception {
      List<Stressor> stressors = new ArrayList<Stressor>();

      startPoint = new CountDownLatch(1);
      for (int threadIndex = 0; threadIndex < numOfThreads; threadIndex++) {
         Stressor stressor = new Stressor(threadIndex, this.nodeIndex, this.perThreadSimulTime, this.arrivalRate, this.paymentWeight, this.orderStatusWeight);
         stressors.add(stressor);
         stressor.start();
      }
      log.info("Cache wrapper info is: " + cacheWrapper.getInfo());
      startPoint.countDown();
      for (Stressor stressor : stressors) {
         stressor.join();
      }
      return stressors;
   }

   private boolean isLocalBenchmark() {
      return nodeIndex == -1;
   }

   private class Stressor extends Thread {

      private int threadIndex;
      private int nodeIndex;
      private long simulTime;
      private double arrivalRate;
      private double paymentWeight;
      private double orderStatusWeight;
      private int nrFailures = 0;
      private int nrWrFailures = 0;
      private int nrWrFailuresOnCommit = 0;
      private int nrRdFailures = 0;
      private int nrNewOrderFailures = 0;
      private int nrPaymentFailures = 0;
      private int appFailures = 0;

      private long readDuration = 0L;
      private long writeDuration = 0L;
      private long newOrderDuration = 0L;
      private long paymentDuration = 0L;
      private long successful_commitWriteDuration = 0L;
      private long aborted_commitWriteDuration = 0L;
      private long commitWriteDuration = 0L;

      private long writeServiceTime = 0L;
      private long newOrderServiceTime = 0L;
      private long paymentServiceTime = 0L;
      private long readServiceTime = 0L;

      private long successful_writeDuration = 0L;
      private long successful_readDuration = 0L;

      private long reads = 0L;
      private long writes = 0L;
      private long payment = 0L;
      private long newOrder = 0L;

      private long numWriteDequeued = 0L;
      private long numReadDequeued = 0L;
      private long numNewOrderDequeued = 0L;
      private long numPaymentDequeued = 0L;

      private long writeInQueueTime = 0L;
      private long readInQueueTime = 0L;
      private long newOrderInQueueTime = 0L;
      private long paymentInQueueTime = 0L;


      public Stressor(int threadIndex, int nodeIndex, long simulTime, double arrivalRate, double paymentWeight, double orderStatusWeight) {
         super("Stressor-" + threadIndex);
         this.threadIndex = threadIndex;
         this.nodeIndex = nodeIndex;
         this.simulTime = simulTime;
         this.arrivalRate = arrivalRate;
         this.paymentWeight = paymentWeight;
         this.orderStatusWeight = orderStatusWeight;

      }

      @Override
      public void run() {

         try {
            startPoint.await();
            log.info("Starting thread: " + getName());
         } catch (InterruptedException e) {
            log.warn(e);
         }

         TpccTerminal terminal = new TpccTerminal(this.paymentWeight, this.orderStatusWeight, this.nodeIndex);

         long delta = 0L;
         long end = 0L;
         long initTime = System.nanoTime();

         long commit_start = 0L;
         long endInQueueTime = 0L;


         TpccTransaction transaction;

         boolean isReadOnly = false;
         boolean successful = true;

         while (delta < (this.simulTime * 1000000000L)) {

            isReadOnly = false;
            successful = true;
            transaction = null;

            long start = System.nanoTime();
            if (arrivalRate != 0.0) {  //Open system
               try {
                  RequestType request = queue.take();

                  endInQueueTime = System.nanoTime();

                  if (request.transactionType == TpccTerminal.NEW_ORDER) {
                     numWriteDequeued++;
                     numNewOrderDequeued++;
                     writeInQueueTime += endInQueueTime - request.timestamp;
                     newOrderInQueueTime += endInQueueTime - request.timestamp;

                     transaction = new NewOrderTransaction();
                  } else if (request.transactionType == TpccTerminal.PAYMENT) {
                     numWriteDequeued++;
                     numPaymentDequeued++;
                     writeInQueueTime += endInQueueTime - request.timestamp;
                     paymentInQueueTime += endInQueueTime - request.timestamp;

                     transaction = new PaymentTransaction(nodeIndex);

                  } else if (request.transactionType == TpccTerminal.ORDER_STATUS) {
                     numReadDequeued++;
                     readInQueueTime += endInQueueTime - request.timestamp;

                     transaction = new OrderStatusTransaction();

                  }


               } catch (InterruptedException ir) {
                  log.error("»»»»»»»THREAD INTERRUPTED WHILE TRYING GETTING AN OBJECT FROM THE QUEUE«««««««");
               }
            } else {

               transaction = terminal.choiceTransaction();
            }
            isReadOnly = transaction.isReadOnly();

            long startService = System.nanoTime();


            cacheWrapper.startTransaction();

            try {
               transaction.executeTransaction(cacheWrapper);
            } catch (Throwable e) {
               successful = false;
               log.warn(e);
               if (e instanceof ElementNotFoundException) {
                  this.appFailures++;
               }

               if (e instanceof Exception) {
                  e.printStackTrace();
               }
            }

            //here we try to finalize the transaction
            //if any read/write has failed we abort
            boolean measureCommitTime = false;
            try {
               /* In our tests we are interested in the commit time spent for write txs*/
               if (successful && !isReadOnly) {
                  commit_start = System.nanoTime();
                  measureCommitTime = true;
               }

               cacheWrapper.endTransaction(successful);

               if (!successful) {
                  nrFailures++;
                  if (!isReadOnly) {
                     nrWrFailures++;
                     if (transaction instanceof NewOrderTransaction) {
                        nrNewOrderFailures++;
                     } else if (transaction instanceof PaymentTransaction) {
                        nrPaymentFailures++;
                     }

                  } else {
                     nrRdFailures++;
                  }

               }
            } catch (Throwable rb) {
               log.info(this.threadIndex + "Error while committing");

               nrFailures++;

               if (!isReadOnly) {
                  nrWrFailures++;
                  nrWrFailuresOnCommit++;
                  if (transaction instanceof NewOrderTransaction) {
                     nrNewOrderFailures++;
                  } else if (transaction instanceof PaymentTransaction) {
                     nrPaymentFailures++;
                  }
               } else {
                  nrRdFailures++;
               }
               successful = false;
               log.warn(rb);

            }


            end = System.nanoTime();


            if (this.arrivalRate == 0.0) {  //Closed system
               start = startService;
            }

            if (!isReadOnly) {
               writeDuration += end - start;
               writeServiceTime += end - startService;
               if (transaction instanceof NewOrderTransaction) {
                  newOrderDuration += end - start;
                  newOrderServiceTime += end - startService;
               } else if (transaction instanceof PaymentTransaction) {
                  paymentDuration += end - start;
                  paymentServiceTime += end - startService;
               }
               if (successful) {
                  successful_writeDuration += end - startService;
                  writes++;
                  if (transaction instanceof PaymentTransaction) {
                     payment++;
                  } else if (transaction instanceof NewOrderTransaction) {
                     newOrder++;
                  }
               }
            } else {
               readDuration += end - start;
               readServiceTime += end - startService;
               if (successful) {
                  reads++;
                  successful_readDuration += end - startService;
               }
            }

            if (measureCommitTime) {
               if (successful) {
                  this.successful_commitWriteDuration += end - commit_start;
               } else {
                  this.aborted_commitWriteDuration += end - commit_start;
               }
               this.commitWriteDuration += end - commit_start;
            }


            delta = end - initTime;
         }

         completedThread.incrementAndGet();

      }


      public long totalDuration() {
         return readDuration + writeDuration;
      }


   }

   private class Producer extends Thread {


      private double transaction_weight;    //an integer in [0,100]
      private int transaction_type;
      private double producerRate;
      private Random random;

      public Producer(int transaction_type, double transaction_weight) {

         this.transaction_weight = transaction_weight;
         this.transaction_type = transaction_type;
         this.producerRate = ((arrivalRate / 1000.0) * (this.transaction_weight / 100.0)) / numSlaves;
         this.random = new Random(System.currentTimeMillis());


      }

      public void run() {

         long time;

         while (completedThread.get() != numOfThreads) {

            try {


               queue.add(new RequestType(System.nanoTime(), this.transaction_type));
               countJobs.incrementAndGet();


               time = (long) (exp(this.producerRate));

               Thread.sleep(time);
            } catch (InterruptedException i) {
               log.error("»»»»»»INTERRUPTED_EXCEPTION«««««««", i);
            } catch (IllegalStateException il) {
               log.error("»»»»»»»IllegalStateException«««««««««", il);

            }
         }

      }

      private double exp(double rate) {

         return -Math.log(1.0 - random.nextDouble()) / rate;
      }


   }

   private class RequestType {

      private long timestamp;
      private int transactionType;

      public RequestType(long timestamp, int transactionType) {
         this.timestamp = timestamp;
         this.transactionType = transactionType;
      }

   }

   private String str(Object o) {
      return String.valueOf(o);
   }

   public void setNumOfThreads(int numOfThreads) {
      this.numOfThreads = numOfThreads;
   }

   public int getNodeIndex() {
      return nodeIndex;
   }

   public void setNodeIndex(int nodeIndex) {
      this.nodeIndex = nodeIndex;
   }

   public void setNumSlaves(int value) {
      this.numSlaves = value;
   }

   public void setPerThreadSimulTime(long perThreadSimulTime) {
      this.perThreadSimulTime = perThreadSimulTime;
   }

   public void setArrivalRate(double arrivalRate) {
      this.arrivalRate = arrivalRate;

   }

   public void setPaymentWeight(double paymentWeight) {
      this.paymentWeight = paymentWeight;
   }

   public void setOrderStatusWeight(double orderStatusWeight) {
      this.orderStatusWeight = orderStatusWeight;
   }

   @Override
   public String toString() {
      return "TpccStressor{" +
            ", perThreadSimulTime=" + perThreadSimulTime +
            ", arrivalRate=" + arrivalRate +
            ", paymentWeight=" + paymentWeight +
            ", orderStatusWeight=" + orderStatusWeight +
            ", numOfThreads=" + numOfThreads +
            ", cacheWrapper=" + cacheWrapper +
            ", nodeIndex=" + nodeIndex +
            "}";
   }

}

package org.radargun.stages.tpcc;

import static java.lang.Double.parseDouble;
import static org.radargun.utils.Utils.numberFormat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.DefaultDistStageAck;
import org.radargun.stages.tpcc.transaction.NewOrderTransaction;
import org.radargun.stages.tpcc.transaction.OrderStatusTransaction;
import org.radargun.stages.tpcc.transaction.PaymentTransaction;
import org.radargun.stages.tpcc.transaction.TpccTransaction;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.CacheInformation;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Transactional;
import org.radargun.utils.Utils;

/**
 * Simulate the activities found in complex OLTP application environments.
 * Execute the TPC-C Benchmark.
 * <pre>
 * Params:
 *       - numThreads : the number of stressor threads that will work on each slave.
 *       - perThreadSimulTime : total time (in seconds) of simulation for each stressor thread.
 *       - arrivalRate : if the value is greater than 0.0, the "open system" mode is active and the parameter represents the arrival rate (in transactions per second) of a job (a transaction to be executed) to the system; otherwise the "closed system" mode is active: this means that each thread generates and executes a new transaction in an iteration as soon as it has completed the previous iteration.
 *       - paymentWeight : percentage of Payment transactions.
 *       - orderStatusWeight : percentage of Order Status transactions.
 * </pre>
 *
 * 
 */
@Stage(doc = "Simulate the activities found in complex OLTP application environments.")
public class TpccBenchmarkStage extends AbstractDistStage {
   
   private static final String SIZE_INFO = "SIZE_INFO";
   public static final String SESSION_PREFIX = "SESSION";
   private static final String REQ_PER_SEC = "REQ_PER_SEC";

   @Property(doc = "Number of threads that will work on this slave. Default is 10.")
   private int numThreads = 10;
   
   @Property(doc = "Total time (in seconds) of simulation for each stressor thread. Default is 180.")
   private long perThreadSimulTime = 180L;
   
   @Property(doc = "Average arrival rate of the transactions to the system. Default is 0.")
   private double arrivalRate = 0.0D;
   
   @Property(doc = "Percentage of Payment transactions. Default is 45 %.")
   private double paymentWeight = 45.0D;
   
   @Property(doc = "Percentage of Order Status transactions. Default is 5 %.")
   private double orderStatusWeight = 5.0D;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private BasicOperations basicOperations;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private CacheInformation cacheInformation;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private Transactional transactional;

   private static Random r = new Random();
   private long startTime;
   private volatile CountDownLatch startPoint;
   private AtomicLong completedThread;
   private BlockingQueue<RequestType> queue;
   private AtomicLong countJobs;
   private Producer[] producers;
   private BasicOperations.Cache<Object, Object> basicCache;
   private Transactional.Resource txCache;

   public DistStageAck executeOnSlave() {
      DefaultDistStageAck result = new DefaultDistStageAck(slaveState.getSlaveIndex(), slaveState.getLocalAddress());
      if (!isServiceRunnning()) {
         log.info("Not running test on this slave.");
         return result;
      }
      basicCache = basicOperations.getCache(null);
      txCache = transactional.getResource(null);

      log.info("Starting TpccBenchmarkStage: " + this.toString());

      try {
         Map<String, Object> results = stress();
         String sizeInfo = "clusterSize:" + slaveState.getClusterSize() + ", nodeIndex:" + slaveState.getSlaveIndex() + ", cacheSize: " + cacheInformation.getCache(null).getLocalSize();
         log.info(sizeInfo);
         results.put(SIZE_INFO, sizeInfo);
         result.setPayload(results);
         return result;
      } catch (Exception e) {
         log.warn("Exception while initializing the test", e);
         result.setError(true);
         result.setRemoteException(e);
         return result;
      }
   }

   public boolean processAckOnMaster(List<DistStageAck> acks) {
      logDurationInfo(acks);
      boolean success = true;
      Map<Integer, Map<String, Object>> results = new HashMap<Integer, Map<String, Object>>();
      // TODO: move this into test report
      masterState.put("results", results);
      for (DistStageAck ack : acks) {
         DefaultDistStageAck wAck = (DefaultDistStageAck) ack;
         if (wAck.isError()) {
            success = false;
            log.warn("Received error ack: " + wAck);
         } else {
            if (log.isTraceEnabled())
               log.trace("Received success ack: " + wAck);
         }
         Map<String, Object> benchResult = (Map<String, Object>) wAck.getPayload();
         if (benchResult != null) {
            results.put(ack.getSlaveIndex(), benchResult);
            Object reqPerSes = benchResult.get(REQ_PER_SEC);
            if (reqPerSes == null) {
               throw new IllegalStateException("This should be there!");
            }
            log.info("On slave " + ack.getSlaveIndex() + " we had " + numberFormat(parseDouble(reqPerSes.toString())) + " requests per second");
            log.info("Received " +  benchResult.remove(SIZE_INFO));
         } else {
            log.trace("No report received from slave: " + ack.getSlaveIndex());
         }
      }
      return success;
   }

   public Map<String, Object> stress() {
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

   private void initializeToolsParameters() {
      try {
         TpccTools.C_C_LAST = (Long) basicCache.get( "C_C_LAST");
         TpccTools.C_C_ID = (Long) basicCache.get("C_C_ID");
         TpccTools.C_OL_I_ID = (Long) basicCache.get("C_OL_ID");
      } catch (Exception e) {
         log.error("Error", e);
      }
   }

   private Map<String, Object> processResults(List<Stressor> stressors) {
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

      Map<String, Object> results = new LinkedHashMap<String, Object>();
      results.put("DURATION (msec)", (duration / numThreads));
      double requestPerSec = (reads + writes) / ((duration / numThreads) / 1000.0);
      results.put(REQ_PER_SEC, requestPerSec);

      double wrtPerSec = 0;
      double rdPerSec = 0;
      double newOrderPerSec = 0;
      double paymentPerSec = 0;

      if (readsDurations + writesDurations == 0)
         results.put("READS_PER_SEC", 0);
      else {
         rdPerSec = reads / (((readsDurations + writesDurations) / numThreads) / 1000000.0);
         results.put("READS_PER_SEC", rdPerSec);
      }

      if (writesDurations + readsDurations == 0)
         results.put("WRITES_PER_SEC", 0);
      else {
         wrtPerSec = writes / (((writesDurations + readsDurations) / numThreads) / 1000000.0);
         results.put("WRITES_PER_SEC", wrtPerSec);
      }

      if (writesDurations + readsDurations == 0)
         results.put("NEW_ORDER_PER_SEC", 0);
      else {
         newOrderPerSec = newOrderTransactions / (((writesDurations + readsDurations) / numThreads) / 1000000.0);
         results.put("NEW_ORDER_PER_SEC", newOrderPerSec);
      }
      if (writesDurations + readsDurations == 0)
         results.put("PAYMENT_PER_SEC", 0);
      else {
         paymentPerSec = paymentTransactions / (((writesDurations + readsDurations) / numThreads) / 1000000.0);
         results.put("PAYMENT_PER_SEC", paymentPerSec);
      }

      results.put("READ_COUNT", reads);
      results.put("WRITE_COUNT", writes);
      results.put("NEW_ORDER_COUNT", newOrderTransactions);
      results.put("PAYMENT_COUNT", paymentTransactions);
      results.put("FAILURES", failures);
      results.put("APPLICATION_FAILURES", appFailures);
      results.put("WRITE_FAILURES", wrFailures);
      results.put("NEW_ORDER_FAILURES", newOrderFailures);
      results.put("PAYMENT_FAILURES", paymentFailures);
      results.put("READ_FAILURES", rdFailures);

      if ((reads + writes) != 0)
         results.put("AVG_SUCCESSFUL_DURATION (usec)", (successful_writesDurations + successful_readsDurations) / (reads + writes));
      else
         results.put("AVG_SUCCESSFUL_DURATION (usec)", 0);

      if (reads != 0)
         results.put("AVG_SUCCESSFUL_READ_DURATION (usec)", successful_readsDurations / reads);
      else
         results.put("AVG_SUCCESSFUL_READ_DURATION (usec)", 0);

      if (writes != 0)
         results.put("AVG_SUCCESSFUL_WRITE_DURATION (usec)", successful_writesDurations / writes);
      else
         results.put("AVG_SUCCESSFUL_WRITE_DURATION (usec)", 0);

      if (writes != 0) {
         results.put("AVG_SUCCESSFUL_COMMIT_WRITE_DURATION (usec)", (successful_commitWriteDurations / writes));
      } else {
         results.put("AVG_SUCCESSFUL_COMMIT_WRITE_DURATION (usec)", 0);
      }

      if (nrWrFailuresOnCommit != 0) {
         results.put("AVG_ABORTED_COMMIT_WRITE_DURATION (usec)", (aborted_commitWriteDurations / nrWrFailuresOnCommit));
      } else {
         results.put("AVG_ABORTED_COMMIT_WRITE_DURATION (usec)", 0);
      }

      if (writes + nrWrFailuresOnCommit != 0) {
         results.put("AVG_COMMIT_WRITE_DURATION (usec)", (commitWriteDurations / (writes + nrWrFailuresOnCommit)));
      } else {
         results.put("AVG_COMMIT_WRITE_DURATION (usec)", 0);
      }

      if ((reads + rdFailures) != 0)
         results.put("AVG_RD_SERVICE_TIME (usec)", readServiceTimes / (reads + rdFailures));
      else
         results.put("AVG_RD_SERVICE_TIME (usec)", 0);

      if ((writes + wrFailures) != 0)
         results.put("AVG_WR_SERVICE_TIME (usec)", writeServiceTimes / (writes + wrFailures));
      else
         results.put("AVG_WR_SERVICE_TIME (usec)", 0);

      if ((newOrderTransactions + newOrderFailures) != 0)
         results.put("AVG_NEW_ORDER_SERVICE_TIME (usec)", newOrderServiceTimes / (newOrderTransactions + newOrderFailures));
      else
         results.put("AVG_NEW_ORDER_SERVICE_TIME (usec)", 0);

      if ((paymentTransactions + paymentFailures) != 0)
         results.put("AVG_PAYMENT_SERVICE_TIME (usec)", paymentServiceTimes / (paymentTransactions + paymentFailures));
      else
         results.put("AVG_PAYMENT_SERVICE_TIME (usec)", 0);

      if (numWritesDequeued != 0)
         results.put("AVG_WR_INQUEUE_TIME (usec)", writeInQueueTimes / numWritesDequeued);
      else
         results.put("AVG_WR_INQUEUE_TIME (usec)", 0);
      if (numReadsDequeued != 0)
         results.put("AVG_RD_INQUEUE_TIME (usec)", readInQueueTimes / numReadsDequeued);
      else
         results.put("AVG_RD_INQUEUE_TIME (usec)", 0);
      if (numNewOrderDequeued != 0)
         results.put("AVG_NEW_ORDER_INQUEUE_TIME (usec)", newOrderInQueueTimes / numNewOrderDequeued);
      else
         results.put("AVG_NEW_ORDER_INQUEUE_TIME (usec)", 0);
      if (numPaymentDequeued != 0)
         results.put("AVG_PAYMENT_INQUEUE_TIME (usec)", paymentInQueueTimes / numPaymentDequeued);
      else
         results.put("AVG_PAYMENT_INQUEUE_TIME (usec)", 0);

      log.info("Finished generating report. Nr of failed operations on this node is: " + failures +
            ". Test duration is: " + Utils.getMillisDurationString(System.currentTimeMillis() - startTime));
      return results;
   }

   private List<Stressor> executeOperations() throws Exception {
      List<Stressor> stressors = new ArrayList<Stressor>();

      startPoint = new CountDownLatch(1);
      for (int threadIndex = 0; threadIndex < numThreads; threadIndex++) {
         Stressor stressor = new Stressor(threadIndex, slaveState.getSlaveIndex(), perThreadSimulTime, arrivalRate, paymentWeight, orderStatusWeight);
         stressors.add(stressor);
         stressor.start();
      }
      startPoint.countDown();
      for (Stressor stressor : stressors) {
         stressor.join();
      }
      return stressors;
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
            log.warn("Interupted!", e);
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
            txCache.startTransaction();
            try {
               transaction.executeTransaction(basicCache);
            } catch (Throwable e) {
               successful = false;
               log.warn("Transaction failed:", e);
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

               txCache.endTransaction(successful);

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
               log.warn(this.threadIndex + "Error while committing", rb);

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
         this.producerRate = ((arrivalRate / 1000.0) * (this.transaction_weight / 100.0)) / slaveState.getClusterSize();
         this.random = new Random(System.currentTimeMillis());
      }

      public void run() {
         long time;
         while (completedThread.get() != numThreads) {
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
}

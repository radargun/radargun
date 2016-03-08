package org.radargun.stages.test;

import org.mockito.Matchers;
import org.mockito.MockSettings;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.modules.testng.PowerMockTestCase;
import org.radargun.Operation;
import org.radargun.stats.Statistics;
import org.radargun.traits.Transactional;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.withSettings;
import static org.powermock.api.mockito.PowerMockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class StressorTest extends PowerMockTestCase {
   static Operation FOO = Operation.register("FOO");
   static Operation BAR = Operation.register("BAR");
   static Operation FAULT = Operation.register("FAULT");
   static Operation TX = Operation.register("TX");

   private final List<InvocationOnMock> unexpectedInvocations = Collections.synchronizedList(new ArrayList<>());
   private final Answer unexpectedAnswer = invocation -> {
      if (invocation.getMethod().getName().equals("finalize")) {
         invocation.callRealMethod();
      }
      unexpectedInvocations.add(invocation);
      throw new IllegalStateException();
   };
   private volatile Statistics statistics = null;
   private volatile Conversation conversation;
   private volatile boolean finished = false;
   private volatile boolean steadyState = false;
   private volatile CountDownLatch createStatisticsLatch = null;
   private volatile CountDownLatch reportStatisticsLatch = null;

   @Test
   public void testNonTransactional() throws InterruptedException {
      test(false);
   }

   @Test
   public void testTransactional() throws InterruptedException {
      test(true);
   }

   public void test(boolean tx) throws InterruptedException {
      // init
      statistics = null;
      conversation = stressor -> {};
      finished = false;
      steadyState = false;
      createStatisticsLatch = null;
      reportStatisticsLatch = null;

      // Mockito has problems when mocking in parallel with actual execution,
      // therefore we need to mock everything ahead through lambdas
      RunningTest test = mock(RunningTest.class, defaultSettings());
      doAnswer(invocation -> finished).when(test).isFinished();
      doAnswer(invocation -> steadyState).when(test).isSteadyState();
      doAnswer(invocation -> {
         Statistics statistics = this.statistics;
         if (statistics == null) {
            unexpectedInvocations.add(invocation);
            throw new IllegalStateException();
         } else {
            createStatisticsLatch.countDown();
            return statistics;
         }
      }).when(test).createStatistics();
      doReturn((ConversationSelector) () -> conversation).when(test).getSelector();
      doAnswer(invocation -> {
         CountDownLatch reportStatisticsLatch = this.reportStatisticsLatch;
         if (reportStatisticsLatch == null) {
            unexpectedInvocations.add(invocation);
            throw new IllegalStateException();
         } else {
            assertEquals(invocation.getArguments()[0], statistics);
            reportStatisticsLatch.countDown();
            return null;
         }
      }).when(test).recordStatistics(Matchers.any(Statistics.class));


      Stressor stressor = new Stressor(0, test, false, false);
      stressor.start();

      for (int cycle = 0; cycle < 10; ++cycle) {
         runSingleCycle(tx);
      }
      finished = true;

      stressor.join();
   }

   protected void runSingleCycle(boolean tx) throws InterruptedException {
      CountDownLatch rampUpLatch = new CountDownLatch(10);
      conversation = stressor -> runConversation(stressor, tx, rampUpLatch, new Foo(), new Bar());

      // wait until 10 conversations are invoked
      assertTrue(rampUpLatch.await(10, TimeUnit.SECONDS));
      assertTrue(unexpectedInvocations.isEmpty(), unexpectedInvocations.toString());

      Statistics statistics = mock(Statistics.class, defaultSettings());
      AtomicInteger begins = new AtomicInteger(0);
      AtomicInteger ends = new AtomicInteger(0);
      ConcurrentMap<Operation, Long> requests = new ConcurrentHashMap<>();
      ConcurrentMap<Operation, Long> errors = new ConcurrentHashMap<>();
      doAnswer(invocation -> {
         begins.incrementAndGet();
         return null;
      }).when(statistics).begin();
      doAnswer(invocation -> {
         ends.incrementAndGet();
         return null;
      }).when(statistics).end();
      doAnswer(invocation -> incrementOps(requests, invocation)).when(statistics).registerRequest(Matchers.anyLong(), Matchers.any(Operation.class));
      doAnswer(invocation -> incrementOps(errors, invocation)).when(statistics).registerError(Matchers.anyLong(), Matchers.any(Operation.class));
      doReturn("").when(statistics).toString();
      this.statistics = statistics;

      // switch to steady state
      CountDownLatch testLatch = new CountDownLatch(10);
      createStatisticsLatch = new CountDownLatch(1);
      steadyState = true;
      createStatisticsLatch.await(10, TimeUnit.SECONDS);
      conversation = stressor -> runConversation(stressor, tx, testLatch, new Foo(), new Bar());

      // check after 10 operations in ready state
      assertTrue(testLatch.await(10, TimeUnit.SECONDS));
      Long foo = requests.get(FOO);
      Long bar = requests.get(BAR);
      Long txs = requests.get(TX);
      Long txBegins = requests.get(Transactional.BEGIN);
      Long commits = requests.get(Transactional.COMMIT);
      Long rollbacks = requests.get(Transactional.ROLLBACK);
      assertTrue(foo != null && foo >= 10, String.valueOf(foo));
      assertTrue(bar != null && bar >= 10, String.valueOf(bar));
      assertTrue(tx ? (txs != null && txs >= 10) : txs == null, String.valueOf(txs));
      assertTrue(tx ? (txBegins != null && txBegins >= 10) : txBegins == null, String.valueOf(txBegins));
      assertTrue(tx ? (commits != null && commits >= 10) : commits == null, String.valueOf(commits));
      assertNull(rollbacks);
      assertTrue(errors.isEmpty(), errors.toString());
      assertEquals(begins.get(), 1);
      assertTrue(unexpectedInvocations.isEmpty(), unexpectedInvocations.toString());

      // set failing conversation
      CountDownLatch failingLatch = new CountDownLatch(10);
      conversation = stressor -> runConversation(stressor, tx, failingLatch, new Foo(), new Fault());

      assertTrue(failingLatch.await(10, TimeUnit.SECONDS));
      foo = requests.get(FOO);
      bar = requests.get(BAR);
      txs = requests.get(TX);
      rollbacks = requests.get(Transactional.ROLLBACK);
      Long fault = errors.get(FAULT);
      assertTrue(foo >= bar + 10, foo + ", " + bar);
      assertNull(requests.get(FAULT));
      assertTrue(fault != null && fault >= 10, String.valueOf(fault));
      assertTrue(tx ? (txs != null && txs >= 10) : txs == null, String.valueOf(txs));
      assertTrue(tx ? (rollbacks != null && rollbacks >= 10) : rollbacks == null, String.valueOf(rollbacks));

      assertTrue(unexpectedInvocations.isEmpty(), unexpectedInvocations.toString());

      // stop steady state
      reportStatisticsLatch = new CountDownLatch(1);
      steadyState = false;

      // wait for statistics to be reported
      assertTrue(reportStatisticsLatch.await(10, TimeUnit.SECONDS));
      assertEquals(begins.get(), 1);
      assertEquals(ends.get(), 1);
      assertTrue(unexpectedInvocations.isEmpty(), unexpectedInvocations.toString());
   }

   private Transactional.Transaction createTransaction() {
      Transactional.Transaction tx = mock(Transactional.Transaction.class, defaultSettings());
      doNothing().when(tx).begin();
      doNothing().when(tx).commit();
      doNothing().when(tx).rollback();
      return tx;
   }

   private void runConversation(Stressor stressor, boolean tx, CountDownLatch latch, Invocation... invocations) {
      try {
         Transactional.Transaction transaction = null;
         if (tx) {
            transaction = createTransaction();
            stressor.startTransaction(transaction);
         }
         try {
            for (Invocation invocation : invocations) {
               assertEquals(stressor.makeRequest(invocation), invocation.operation());
            }
            if (tx) {
               stressor.commitTransaction(transaction, TX);
            }
         } catch (Throwable t) {
            if (tx) {
               stressor.rollbackTransaction(transaction, TX);
            }
            throw t;
         }
      } finally {
         latch.countDown();
      }
   }

   protected Object incrementOps(ConcurrentMap<Operation, Long> requests, InvocationOnMock invocation) {
      Operation operation = (Operation) invocation.getArguments()[1];
      Long reqs = requests.get(operation);
      requests.put(operation, reqs == null ? 1 : reqs + 1);
      return null;
   }

   private MockSettings defaultSettings() {
      return withSettings().defaultAnswer(unexpectedAnswer);
   }

   private static class Foo implements Invocation {
      @Override
      public Object invoke() {
         return FOO;
      }

      @Override
      public Operation operation() {
         return FOO;
      }

      @Override
      public Operation txOperation() {
         return FOO;
      }
   }

   private static class Bar implements Invocation {
      @Override
      public Object invoke() {
         return BAR;
      }

      @Override
      public Operation operation() {
         return BAR;
      }

      @Override
      public Operation txOperation() {
         return BAR;
      }
   }

   private static class Fault implements Invocation {
      @Override
      public Object invoke() {
         throw new RuntimeException();
      }

      @Override
      public Operation operation() {
         return FAULT;
      }

      @Override
      public Operation txOperation() {
         return FAULT;
      }
   }
}

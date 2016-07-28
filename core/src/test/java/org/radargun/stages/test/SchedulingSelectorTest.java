package org.radargun.stages.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.testng.PowerMockTestCase;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.utils.TimeService;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

@Test
@PowerMockIgnore({"javax.management.*"})
@PrepareForTest(TimeService.class)
public class SchedulingSelectorTest extends PowerMockTestCase {
   private static Log log = LogFactory.getLog(SchedulingSelectorTest.class);
   private ExecutorService executorService = Executors.newFixedThreadPool(1);

   @Test
   public void test() throws InterruptedException {
      PowerMockito.mockStatic(TimeService.class);
      setTime(0);
      SchedulingSelector<String> selector = new SchedulingSelector.Builder<>(String.class)
         .add("a", 2, 5L)
         .add("b", 3, 3L)
         .build();
      checkReturns(selector, "a", "a", "b", "b", "b");
      checkBlocks(selector);
      setTime(1);
      checkBlocks(selector);
      setTime(2);
      checkBlocks(selector);
      setTime(3);
      checkReturns(selector, "b", "b", "b");
      checkBlocks(selector);
      setTime(4);
      checkBlocks(selector);
      setTime(5);
      checkReturns(selector, "a", "a");
      checkBlocks(selector);
      setTime(6);
      checkReturns(selector, "b", "b", "b");
      checkBlocks(selector);
      setTime(7);
      checkBlocks(selector);
      setTime(8);
      checkBlocks(selector);
      setTime(9);
      checkReturns(selector, "b", "b", "b");
      setTime(10);
      checkReturns(selector, "a", "a");
      checkBlocks(selector);
      setTime(11);
      checkBlocks(selector);
      setTime(12);
      checkReturns(selector, "b", "b", "b");
      checkBlocks(selector);
      setTime(13);
      checkBlocks(selector);
      setTime(14);
      checkBlocks(selector);
      setTime(15);
      checkReturns(selector, "a", "a", "b", "b", "b");
      checkBlocks(selector);
   }

   protected void setTime(long value) {
      PowerMockito.when(TimeService.currentTimeMillis()).thenReturn(value);
   }

   protected void checkReturns(SchedulingSelector<String> selector, String... expected) throws InterruptedException {
      List<String> retvals = Collections.synchronizedList(new ArrayList<>());
      CountDownLatch latch = new CountDownLatch(1);
      executorService.execute(() -> {
         for (int i = 0; i < expected.length; ++i) {
            try {
               String next = selector.next();
               retvals.add(next);
            } catch (InterruptedException e) {
               log.error("Unexpected interruption", e);
               break;
            }
         }
         latch.countDown();
      });
      assertTrue(latch.await(10, TimeUnit.SECONDS));
      assertEquals(retvals.size(), expected.length);
      Collections.sort(retvals);
      assertEquals(retvals, Arrays.asList(expected));
   }

   protected void checkBlocks(SchedulingSelector<String> selector) throws InterruptedException {
      Future<?> task = executorService.submit(() -> {
         try {
            selector.next();
         } catch (InterruptedException e) {
            log.trace("Expected interruption", e);
         }
      });
      try {
         task.get(100, TimeUnit.MILLISECONDS);
         fail("Unexpected return");
      } catch (ExecutionException e) {
         fail("Unexpected exception", e);
      } catch (TimeoutException e) {
         task.cancel(true);
      }
   }
}

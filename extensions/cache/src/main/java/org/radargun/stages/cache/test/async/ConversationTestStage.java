package org.radargun.stages.cache.test.async;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.radargun.Operation;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.logging.Log;
import org.radargun.stages.test.async.Conversation;
import org.radargun.stages.test.async.SchedulingSelector;
import org.radargun.traits.BasicAsyncOperations;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Transactional;
import org.radargun.utils.ReflexiveConverters;
import org.radargun.utils.TimeConverter;

@Stage(doc = "Benchmark for composed conversations, including transactional ones.")
public class ConversationTestStage extends CacheAsyncTestStage implements ContextProvider {

   @Property(doc = "List of possible conversations", complexConverter = ConversationInvocation.ListConverter.class)
   protected List<ConversationInvocation> conversations;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   protected BasicAsyncOperations basicAsyncOperations;

   @InjectTrait
   protected Transactional transactional;

   protected BasicAsyncOperations.Cache cache;
   protected ScheduledExecutorService scheduledExecutorService;
   protected ExecutorService executor;

   @Override
   protected SchedulingSelector<Conversation> createSelector() {
      SchedulingSelector.Builder<Conversation> builder = new SchedulingSelector.Builder<>(Conversation.class);
      for (ConversationInvocation ci : conversations) {
         Operation operation = Operation.register(ci.name);
         ConversationStep[] steps = ci.steps.toArray(new ConversationStep[ci.steps.size()]);
         builder.add(new ComposedConversation(operation, steps, this, ci.transactional), ci.invocations, ci.interval);
      }
      return builder.build();
   }

   @Override
   protected void prepare() {
      super.prepare();
      AtomicInteger threadCounter = new AtomicInteger();
      scheduledExecutorService = new ScheduledThreadPoolExecutor(numThreads, r -> new Thread(r, testName + "-sched-" + threadCounter.incrementAndGet()));
      executor = new ThreadPoolExecutor(numThreads, numThreads, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<>(),
         r -> new Thread(r, testName + "-exec-" + threadCounter.incrementAndGet()));
      cache = basicAsyncOperations.getCache(cacheSelector.getCacheName(-1));
   }

   @Override
   protected void destroy() {
      scheduledExecutorService.shutdownNow();
      executor.shutdownNow();
   }

   @Override
   public BasicAsyncOperations.Cache cache() {
      return cache;
   }

   @Override
   public ScheduledExecutorService scheduledExecutor() {
      return scheduledExecutorService;
   }

   @Override
   public Executor executor() {
      return executor;
   }

   @Override
   public Transactional.Transaction transaction() {
      return transactional.getTransaction();
   }

   @Override
   public Log log() {
      return log;
   }

   @DefinitionElement(name = "conversation", doc = "Description of an conversation")
   public static class ConversationInvocation {
      @Property(doc = "Name of the given conversations")
      protected String name;

      @Property(doc = "Number of invocations of given operation per interval (see property interval), on each node. Default is 0.")
      public int invocations = 0;

      @Property(doc = "Size of the slot in milliseconds. Raising this risks having bursts" +
            "at the beginning of the interval. Default is 1 ms.", converter = TimeConverter.class)
      public long interval = 1;

      @Property(doc = "Should these operations be wrapped in a transaction? Default is false.")
      public boolean transactional;

      @Property(doc = "List of steps in given conversation", complexConverter = ConversationStep.ListConverter.class)
      protected List<ConversationStep> steps;

      public static class ListConverter extends ReflexiveConverters.ListConverter {
         public ListConverter() {
            super(new Class[] { ConversationInvocation.class });
         }
      }
   }
}

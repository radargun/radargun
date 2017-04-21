package org.radargun.stages.cache.test.async;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;

import org.radargun.logging.Log;
import org.radargun.traits.BasicAsyncOperations;
import org.radargun.traits.Transactional;

/**
 * Abstracts the stage and {@link ConversationStep conversation steps}.
 */
interface ContextProvider {
   // TODO: retrieve trait in a generic way?
   BasicAsyncOperations.Cache<Object, Object> cache();
   Transactional.Transaction transaction();

   Object getRandomKey(ThreadLocalRandom random);
   Object getRandomValue(ThreadLocalRandom random, Object key);

   ScheduledExecutorService scheduledExecutor();
   Executor executor();
   ThreadGroup stressorGroup();

   Log log();
}

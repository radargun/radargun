package org.radargun.stages.cache.test.legacy;

import java.io.InputStream;
import java.util.Random;

import org.radargun.Operation;
import org.radargun.config.Namespace;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.cache.test.CacheInvocations;
import org.radargun.stages.test.Invocation;
import org.radargun.stages.test.legacy.LegacyStressor;
import org.radargun.stages.test.legacy.LegacyTestStage;
import org.radargun.stages.test.legacy.OperationLogic;
import org.radargun.stages.test.legacy.OperationSelector;
import org.radargun.stages.test.legacy.RatioOperationSelector;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.StreamingOperations;

@Namespace(LegacyTestStage.NAMESPACE)
@Stage(doc = "Streaming operations test stage")
public class StreamingOperationsTestStage extends CacheOperationsTestStage {

   @Property(doc = "Ratio of GET requests. Default is 4.")
   protected int getRatio = 4;

   @Property(doc = "Ratio of PUT requests. Default is 1.")
   protected int putRatio = 1;

   @Property(doc = "Streaming operations buffer size in bytes, default is 100")
   protected int bufferSize = 100;

   @InjectTrait
   protected StreamingOperations streamingOperations;

   @Override
   protected OperationSelector createOperationSelector() {
      return new RatioOperationSelector.Builder().add(StreamingOperations.GET, getRatio).add(StreamingOperations.PUT, putRatio)
            .build();
   }

   public OperationLogic getLogic() {
      return new Logic();
   }

   protected class Logic extends OperationLogic {
      protected Long version = null;

      protected KeySelector keySelector;
      protected StreamingOperations.StreamingCache<Object> cache;

      protected byte[] buffer = new byte[bufferSize];

      @Override
      public void init(LegacyStressor stressor) {
         super.init(stressor);

         String cacheName = cacheSelector.getCacheName(stressor.getGlobalThreadIndex());
         this.cache = streamingOperations.getStreamingCache(cacheName);
         this.keySelector = getKeySelector(stressor);
      }

      @Override
      public void run(Operation operation) throws RequestException {
         Object key = keyGenerator.generateKey(keySelector.next());
         Random random = stressor.getRandom();

         Invocation invocation;
         if (operation == StreamingOperations.GET) {
            invocation = new CacheInvocations.GetViaStream<Object, Integer>(cache, key, buffer);
         } else if (operation == StreamingOperations.PUT) {
            invocation = new CacheInvocations.PutViaStream<Object, InputStream>(cache, key,
                  (InputStream) valueGenerator.generateValue(key, entrySize.next(random), random), buffer);
         } else
            throw new IllegalArgumentException(operation.name);
         stressor.makeRequest(invocation);
      }
   }

}

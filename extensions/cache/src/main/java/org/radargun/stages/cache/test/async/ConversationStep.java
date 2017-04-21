package org.radargun.stages.cache.test.async;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.radargun.utils.ReflexiveConverters;

interface ConversationStep extends Function<ConversationContext, CompletableFuture<?>> {
   class ListConverter extends ReflexiveConverters.ListConverter {
      public ListConverter() {
         super(ConversationStep.class);
      }
   }
}

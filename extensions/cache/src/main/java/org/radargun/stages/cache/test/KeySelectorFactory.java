package org.radargun.stages.cache.test;

import java.util.Random;

/**
 * Factory for creating selectors. Usually holds properties defining number of keys used
 * and thread-key allocations, and also is marked by {@link org.radargun.config.DefinitionElement}
 * to be able to be created through reflection.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface KeySelectorFactory {
   KeySelector newInstance(CacheOperationsTestStage stage, Random random, int globalThreadId, int threadId);
}

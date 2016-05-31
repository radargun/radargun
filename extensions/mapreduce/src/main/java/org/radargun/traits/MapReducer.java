package org.radargun.traits;

import java.util.Collection;
import java.util.Map;

import org.radargun.Operation;
import org.radargun.utils.KeyValueProperty;

/*
 * TODO: create documentation how M/R tasks should work at all; now we use Infinispan model
 * as this is the only implementation, but we should generalize it here (as it's far from obvious).
 *
 * Preferred design: Avoid the need to use plugin-specific classes (e.g. org.infinispan.demo.mapreduce.WordCountMapperEmitPerValue,
 * org.radargun.service.demo.ispn.WordCountMapper). This makes scenario definition with multiple plugins clumsy to use.
 * Instead, provide single problem definition (e.g. org.radargun.xxx.WordCountMapper for word count example), which will use high-level api (HLA)
 * provided by RadarGun. It'll be up to plugins to provide mapping of HLA to product-specific constructs.
 */
@Trait(doc = "Provides interface for executing Map/Reduce tasks")
public interface MapReducer<KOut, VOut, R> {
   String TRAIT = MapReducer.class.getSimpleName();
   Operation MAPREDUCE = Operation.register(TRAIT + ".MapReduce");
   Operation MAPREDUCE_COLLATOR = Operation.register(TRAIT + ".MapReduceWithCollator");

   interface Builder<KOut, VOut, R> {
      /**
       *
       * Set a timeout (ms) for the communication between the nodes during a Map Reduce task. Setting this
       * value to zero or less than zero means to wait forever.
       *
       * @param timeout
       *           the value of the timeout
       * @throws {@link java.lang.UnsupportedOperationException} if {@link #supportsTimeout()} returns false
       */
      Builder<KOut, VOut, R> timeout(long timeout);

      /**
       * @param source
       *           Name of the source to execute map-reduce task on. Interpretation is implementation-specific.
       * @return this builder instance
       */
      Builder<KOut, VOut, R> source(String source);

      /**
       * @param mapperFqn
       *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Mapper
       *           implementation. The implementation must have a no argument constructor.
       * @param mapperParameters
       *           parameters for the Mapper object
       * @return this builder instance
       */
      Builder<KOut, VOut, R> mapper(String mapperFqn, Collection<KeyValueProperty> mapperParameters);

      /**
       * @param reducerFqn
       *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Reducer
       *           implementation. The implementation must have a no argument constructor.
       *
       * @param reducerParameters
       *           parameters for the Reducer object
       * @return this builder instance
       */
      Builder<KOut, VOut, R> reducer(String reducerFqn, Collection<KeyValueProperty> reducerParameters);

      /**
       *
       * Specifies a Reducer class to be used with the MapReduceTask during the combine phase
       *
       * @param combinerFqn
       *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Reducer
       *           implementation. The implementation must have a no argument constructor.
       * @param combinerParameters
       *           parameters for the Reducer object used as a combiner
       * @return this builder instance
       * @throws {@link java.lang.UnsupportedOperationException} if {@link #supportsCombiner()} returns false
       */
      Builder<KOut, VOut, R> combiner(String combinerFqn, Collection<KeyValueProperty> combinerParameters);

      /**
       *
       * Set the specified collator on the supplied MapTask
       *
       * @param collatorFqn
       *           the fully qualified class name for the Collator implementation. The
       *           implementation must have a no argument constructor.
       * @param collatorParameters
       *           parameters for the Collator object
       */
      Builder<KOut, VOut, R> collator(String collatorFqn, Collection<KeyValueProperty> collatorParameters);

      /**
       * @return The task to be executed
       */
      Task<KOut, VOut, R> build();
   }

   interface Task<KOut, VOut, R> {
      /**
       *
       * Execute the MapTask against all of the keys in the cache
       *
       * @return a Map where each key is an output key and value is reduced value for that output
       *         key
       */
      Map<KOut, VOut> execute();

      /**
       *
       * Execute the MapTask against all of the keys in the cache and use a Collator on the results
       *
       * @return the collated result object
       */
      R executeWithCollator();
   }

   /**
    * Create a new builder.
    * @return the builder instance
    */
   Builder<KOut, VOut, R> builder();

   /**
    * @return <code>true</code> if the trait supports using combiner, else <code>false</code>
    */
   boolean supportsCombiner();

   /**
    * @return <code>true</code> if the trait supports setting the timeout, else <code>false</code>
    */
   boolean supportsTimeout();

}

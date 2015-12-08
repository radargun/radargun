package org.radargun.service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.api.java.function.PairFunction;
import org.radargun.traits.MapReducer;
import org.radargun.utils.Utils;

/**
 * @author Matej Cimbora
 */
public class SparkMapReduce implements MapReducer, Serializable {

   private SparkDriverService sparkDriverService;

   public SparkMapReduce(SparkDriverService sparkDriverService) {
      this.sparkDriverService = sparkDriverService;
   }

   @Override
   public Builder builder(String cacheName) {
      return new Builder(this);
   }

   @Override
   public boolean supportsResultCacheName() {
      return false;
   }

   @Override
   public boolean supportsIntermediateSharedCache() {
      return false;
   }

   @Override
   public boolean supportsCombiner() {
      return false;
   }

   @Override
   public boolean supportsTimeout() {
      return false;
   }

   @Override
   public boolean supportsDistributedReducePhase() {
      return false;
   }

   public static class Builder implements MapReducer.Builder {

      private SparkMapReduce sparkMapReduce;

      private Object source;
      private Object mapper;
      private Object reducer;

      public Builder(SparkMapReduce sparkMapReduce) {
         this.sparkMapReduce = sparkMapReduce;
      }

      @Override
      public MapReducer.Builder distributedReducePhase(boolean distributedReducePhase) {
         throw new UnsupportedOperationException("Distributed reduce phase not supported");
      }

      @Override
      public MapReducer.Builder useIntermediateSharedCache(boolean useIntermediateSharedCache) {
         throw new UnsupportedOperationException("Intermediate shared cache not supported");
      }

      @Override
      public MapReducer.Builder timeout(long timeout, TimeUnit unit) {
         throw new UnsupportedOperationException("Timeout not supported");
      }

      @Override
      public MapReducer.Builder resultCacheName(String resultCacheName) {
         throw new UnsupportedOperationException("Result cache name not supported");
      }

      @Override
      public MapReducer.Task build() {
         final SparkDriverService sparkDriverService = sparkMapReduce.sparkDriverService;
         try {
            source = Utils.instantiate(sparkDriverService.sourceClass);
            source = Utils.invokeMethodWithProperties(source, sparkDriverService.sourceProperties);
         } catch (Exception e) {
            throw new IllegalArgumentException("Could not instantiate RDD source class: " + sparkDriverService.sourceClass, e);
         }
         if (mapper instanceof SparkMapper) {
            return new MapReduceTask((SparkMapper) mapper, (SparkReducer) reducer, (SparkJavaRDDSource) source, sparkDriverService.sparkContext);
         } else if (mapper instanceof SparkPairMapper) {
            return new MapToPairReduceByKeyTask((SparkPairMapper) mapper, (SparkReducer) reducer, (SparkJavaRDDSource) source, sparkDriverService.sparkContext);
         } else {
            throw new IllegalStateException("Invalid Mapper implementation " + mapper + " has been provided. " +
                                                  "Expecting one of (" + SparkMapper.class + ", " + SparkPairMapper.class + ")");
         }
      }

      @Override
      public MapReducer.Builder collator(String collatorFqn, Map collatorParameters) {
         throw new UnsupportedOperationException("Collator not supported");
      }

      @Override
      public MapReducer.Builder combiner(String combinerFqn, Map combinerParameters) {
         throw new UnsupportedOperationException("Combiner not supported");
      }

      @Override
      public MapReducer.Builder reducer(String reducerFqn, Map reducerParameters) {
         try {
            reducer = Utils.instantiate(reducerFqn);
            Utils.invokeMethodWithString(reducer, reducerParameters);
         } catch (Exception e) {
            throw new IllegalArgumentException("Could not instantiate Reducer class: " + reducerFqn, e);
         }
         return this;
      }

      @Override
      public MapReducer.Builder mapper(String mapperFqn, Map mapperParameters) {
         try {
            mapper = Utils.instantiate(mapperFqn);
            Utils.invokeMethodWithString(mapper, mapperParameters);
         } catch (Exception e) {
            throw new IllegalArgumentException("Could not instantiate Mapper class: " + mapperFqn, e);
         }
         return this;
      }
   }

   public abstract static class AbstractTask implements MapReducer.Task, Serializable {

      protected SparkReducer reducer;
      protected SparkJavaRDDSource source;
      protected JavaSparkContext sparkContext;

      public AbstractTask(SparkReducer reducer, SparkJavaRDDSource source, JavaSparkContext javaSparkContext) {
         this.reducer = reducer;
         this.source = source;
         this.sparkContext = javaSparkContext;
         source.setSparkContext(sparkContext);

         // Run dummy task to make sure jars are added to workers before performance test starts
         sparkContext.parallelize(new ArrayList<>(0)).count();
      }
   }

   public static class MapReduceTask extends AbstractTask {

      private SparkMapper mapper;

      public MapReduceTask(SparkMapper mapper, SparkReducer reducer, SparkJavaRDDSource source, JavaSparkContext javaSparkContext) {
         super(reducer, source, javaSparkContext);
         this.mapper = mapper;
         this.reducer = reducer;
      }

      @Override
      public Map execute() {
         Object resultObject = source.getSource().map(mapper.getMapFunction()).reduce(reducer.getReduceFunction());
         Map resultMap = new HashMap(1);
         resultMap.put("result_key", resultObject);
         return resultMap;
      }

      @Override
      public Object executeWithCollator() {
         throw new UnsupportedOperationException("Collator not supported");
      }
   }

   public static class MapToPairReduceByKeyTask extends AbstractTask {

      private SparkPairMapper mapper;

      public MapToPairReduceByKeyTask(SparkPairMapper mapper, SparkReducer reducer, SparkJavaRDDSource source, JavaSparkContext javaSparkContext) {
         super(reducer, source, javaSparkContext);
         this.mapper = mapper;
      }

      @Override
      public Map execute() {
         JavaPairRDD javaPairRDD = source.getSource().mapToPair(mapper.getMapFunction()).reduceByKey(reducer.getReduceFunction());
         return javaPairRDD.collectAsMap();
      }

      @Override
      public Object executeWithCollator() {
         throw new UnsupportedOperationException("Collator not supported");
      }

   }

   /**
    * Provides JavaRDD from various sources (e.g. file, ISPN cluster, parallelized collection)
    */
   public interface SparkJavaRDDSource<T> extends Serializable {
      JavaRDD<T> getSource();

      /**
       * Set spark context to obtain JavaRDD with
       */
      void setSparkContext(JavaSparkContext context);
   }

   /**
    * Basic mapper implementation allowing to invoke JavaRDD.map() function
    */
   public interface SparkMapper<T, R> extends Serializable {
      Function<T, R> getMapFunction();
   }

   /**
    * Mapper implementation allowing to invoke JavaRDD.mapToPair() function
    */
   public interface SparkPairMapper<T, K, V> extends Serializable {
      PairFunction<T, K, V> getMapFunction();
   }

   /*
      Reducer implementation
    */
   public interface SparkReducer<T1, T2, R> extends Serializable {
      Function2<T1, T2, R> getReduceFunction();
   }


}

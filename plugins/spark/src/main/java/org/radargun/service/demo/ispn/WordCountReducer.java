package org.radargun.service.demo.ispn;

import org.apache.spark.api.java.function.Function2;
import org.radargun.service.SparkMapReduce;

/**
 * @author Matej Cimbora
 */
public class WordCountReducer implements SparkMapReduce.SparkReducer<Integer, Integer, Integer> {

   public WordCountReducer() {}

   @Override
   public Function2<Integer, Integer, Integer> getReduceFunction() {
      return (a, b) -> a + b;
   }
}

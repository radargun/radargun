package org.radargun.service.demo.ispn;

import org.apache.spark.api.java.function.PairFunction;
import org.radargun.service.SparkMapReduce;
import scala.Tuple2;

/**
 * @author Matej Cimbora
 */
public class WordCountMapper implements SparkMapReduce.SparkPairMapper<String, String, Integer> {

   public WordCountMapper() {}

   @Override
   public PairFunction<String, String, Integer> getMapFunction() {
      return s -> new Tuple2<>(s, 1);
   }
}

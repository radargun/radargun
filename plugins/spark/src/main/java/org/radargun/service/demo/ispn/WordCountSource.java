package org.radargun.service.demo.ispn;

import java.util.Properties;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.infinispan.spark.rdd.InfinispanJavaRDD;
import org.radargun.service.SparkMapReduce;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

/**
 * @author Matej Cimbora
 */
public class WordCountSource implements SparkMapReduce.SparkJavaRDDSource<String> {

   private transient JavaSparkContext sparkContext;
   private transient String hotrodServer = "127.0.0.1";
   private transient String hotrodPort = "11222";
   private transient int numPartitions = 2;

   public WordCountSource() {}

   @Override
   public JavaRDD<String> getSource() {
      if (sparkContext == null) {
         throw new IllegalStateException("Spark context has not been initialized yet");
      }
      Properties infinispanProperties = new Properties();
      infinispanProperties.put("infinispan.client.hotrod.server_list", hotrodServer + ":" + hotrodPort);
      infinispanProperties.put("infinispan.rdd.number_server_partitions", numPartitions);
      JavaPairRDD<String, String> javaPairRDD = InfinispanJavaRDD.createInfinispanRDD(sparkContext, infinispanProperties);
      JavaRDD<String> javaRDD = javaPairRDD.values().flatMap(s -> stream(s.split("[\\p{Punct}\\s&&[^'-]]+")).collect(toList()));
      return javaRDD;
   }

   @Override
   public void setSparkContext(JavaSparkContext context) {
      this.sparkContext = context;
   }

   public void setHotrodServer(String address) {
      this.hotrodServer = address;
   }

   public void setHotrodPort(String port) {
      this.hotrodPort = port;
   }

   public void setNumPartitions(String numPartitions) {
      this.numPartitions = Integer.parseInt(numPartitions);
   }
}

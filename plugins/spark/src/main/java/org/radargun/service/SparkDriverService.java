package org.radargun.service;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.radargun.Directories;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.KeyValueProperty;
import org.radargun.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author Matej Cimbora
 */
@Service(doc = "Class encapsulating Apache Spark driver")
public class SparkDriverService {

   @Property(doc = "Source class providing JavaRDD. Default is null.")
   protected String sourceClass;

   @Property(doc = "A list of key-value pairs in the form of " +
         "methodName:methodParameter;methodName1:methodParameter1' that allows" +
         " invoking a method on the Source Object. The method" +
         " must be public and take a String parameter. The default is null.", complexConverter = KeyValueProperty.KeyValuePairListConverter.class)
   protected List<KeyValueProperty> sourceProperties;

   @Property(doc = "Name of the host where master node is deployed. Default is localhost.")
   protected String host = "localhost";

   @Property(doc = "Port under which master node is accessible. Default is 7077.")
   protected int port = 7077;

   @Property(doc = "Name of the application to be deployed. Default is testApp.")
   protected String appName = "testApp";

   @Property(doc = "Custom properties passed to JavaSparkContext. Default is null", complexConverter = KeyValueProperty.KeyValuePairListConverter.class)
   protected List<KeyValueProperty> properties;

   protected JavaSparkContext sparkContext;

   @ProvidesTrait
   public SparkDriverLifecycle createSparkDriverLifecycle() {
      return new SparkDriverLifecycle(this);
   }

   @ProvidesTrait
   public SparkMapReduce createSparkMapReduce() {
      return new SparkMapReduce(this);
   }

   protected void startSparkContext() {
      SparkConf sparkConf = new SparkConf();
      sparkConf.setMaster("spark://" + host + ":" + port).setAppName(appName);
      if (properties != null) {
         properties.forEach(p -> sparkConf.set(p.getKey(), p.getValue()));
      }
      sparkContext = new JavaSparkContext(sparkConf);
      addJarsToContext();
   }

   private void addJarsToContext() {
      // Add core jar
      sparkContext.addJar(Directories.class.getProtectionDomain().getCodeSource().getLocation().getPath());
      // Add jars from lib folder
      File dir = new File(SparkMapReduce.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParentFile();
      for (File file : dir.listFiles((new Utils.JarFilenameFilter()))) {
         try {
            sparkContext.addJar(file.getCanonicalPath());
         } catch (IOException e) {
            throw new IllegalStateException(e);
         }
      }
   }
}

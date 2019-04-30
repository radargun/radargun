package org.radargun.service;

import com.couchbase.client.core.metrics.DefaultLatencyMetricsCollectorConfig;
import com.couchbase.client.core.metrics.DefaultMetricsCollectorConfig;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;

import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.traits.Lifecycle;

@Service(doc = "Couchbase")
public class CouchbaseClientService implements Lifecycle {

   protected Cluster cluster;
   protected Bucket bucket;

   @Property(doc = "Bootstrap nodes")
   protected String nodes = "localhost";

   @Property(doc = "Username")
   protected String username = "Administrator";

   @Property(doc = "Password")
   protected String password = "password";

   @Property(doc = "Bucket name")
   protected String bucketName = "default";

   @Override
   public void start() {
      CouchbaseEnvironment env = DefaultCouchbaseEnvironment
         .builder()
         .callbacksOnIoPool(true)
         .runtimeMetricsCollectorConfig(DefaultMetricsCollectorConfig.disabled())
         .networkLatencyMetricsCollectorConfig(DefaultLatencyMetricsCollectorConfig.disabled())
         .build();

      cluster = CouchbaseCluster.create(env, nodes);
      cluster.authenticate(username, password);
      bucket = cluster.openBucket(bucketName);
   }

   @Override
   public void stop() {
      cluster.disconnect();
      cluster = null;
   }

   @Override
   public boolean isRunning() {
      return cluster != null;
   }
}
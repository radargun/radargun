package org.radargun.service;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.Utils;
import redis.embedded.RedisExecProvider;
import redis.embedded.RedisServer;
import redis.embedded.util.OS;

/**
 *
 * Usage instructions:
 * You need to compile redis on your target test system, and point to its directory with the distributionDir property.
 * Alternatively you can zip the compiled distribution, point to the zip with the distributionZip property and RG will unzip it to distributionDir for you.
 *
 * You cannot configure the port in redis configuration file, because the embedded-redis library this service uses to start
 * redis from java is overwriting it. You need to use the port property if you want to change the default.
 *
 * To cluster multiple redis instances, you need to call a redis-trib.rb ruby script (see https://redis.io/topics/cluster-tutorial).
 * To do this, add a stage like this one after the <service-start> stage:
 * <command slaves="0" cmd="sh" args="-c" non-parsed-args="echo yes | <path-to-redis-distro>/redis-3.2.8/src/redis-trib.rb create --replicas 1 192.168.11.101:6379 192.168.11.102:6379 192.168.11.103:6379 192.168.11.104:6379 192.168.11.105:6379 192.168.11.106:6379" exit-values="0"/>
 *
 * Note that redis saves some data in the distribution directory, and you need to delete it between runs. Using the distributionZip
 * property is preferred, so the distribution is 'clean' with each run.
 *
 */
@Service(doc = "Redis")
public class RedisService implements Lifecycle {

   protected RedisServer server;

   @Property(name = Service.FILE, doc = "Configuration file.")
   protected String config;

   @Property(doc = "Redis port")
   protected Integer port = 6379;

   @Property(doc = "Directory of the redis distribution.", optional = false)
   protected String distributionDir;

   @Property(doc = "Distribution zip. If set, the zip will be extracted to the 'distributionDir' directory.")
   protected String distributionZip;

   @ProvidesTrait
   public RedisService getSelf() {
      return this;
   }

   @Override
   public void start() {

      try {
         if (distributionZip != null) {
            Utils.unzip(distributionZip, distributionDir);
            // the extraction erases the executable bits
            Utils.setPermissions(distributionDir + "/src/redis-server", "rwxr-xr-x");
            Utils.setPermissions(distributionDir + "/src/redis-trib.rb", "rwxr-xr-x");
         }
      } catch (IOException e) {
         throw new RuntimeException("Failed to prepare redis distribution!", e);
      }

      RedisExecProvider provider = RedisExecProvider.build().override(OS.UNIX, distributionDir + "/src/redis-server");
      server = new RedisServer.Builder().redisExecProvider(provider).port(port).configFile(config).build();
      try {
         server.start();
      } catch (Exception e) {
         try {
            throw new RuntimeException("Start of redis failed. Redis logs: " + IOUtils.toString(server.errors(), "UTF-8"));
         } catch (IOException e1) {
            throw new RuntimeException("Start of redis failed.", e1);
         }
      }
   }

   @Override
   public void stop() {
      server.stop();
   }

   @Override
   public boolean isRunning() {
      return server != null && server.isActive();
   }

}

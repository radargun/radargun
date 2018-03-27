package org.radargun.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.radargun.Service;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.utils.KeyValueProperty;

/**
 *
 * Usage instructions:
 * You need to compile etcd on your target test system, and point to its directory with the distributionDir property.
 * Alternatively you can zip the compiled distribution, point to the zip with the distributionZip property and RG will unzip it to distributionDir for you.
 *
 * Note that etcd saves some data in the distribution directory, and you need to delete it between runs. Using the distributionZip
 * property is preferred, so the distribution is 'clean' with each run.
 *
 * The documentation about the the configuration is available at https://coreos.com/etcd/docs/latest/op-guide/configuration.html
 *
 */
@Service(doc = "Etcd")
public class EtcdServerService extends ProcessService {

   @Property(doc = "Distribution zip. The zip will be extracted to the 'distributionDir' directory.", optional = false)
   protected String distributionZip;

   /**
    * It is realted with the --discovery member flag.
    */
   @Property(doc = "Prefix flags need to be set when using discovery service.", optional = false)
   protected String discovery;

   @Property(doc = "Server ip address.", optional = false)
   protected String ip;

   @Property(doc = "Directory of the etcd distribution.")
   protected String distributionDir;

   /**
    * It is related with the --listen-port-urls member flag. In this case the hostname will be dynamically full filled.
    */
   @Property(doc = "The listen port number. Default is 2379")
   protected int listenClientPort = 2379;

   /**
    * It is related with the --listen-peer-urls member flag. In this case the hostname will be dynamically full filled.
    */
   @Property(doc = "The listen peer port port number. Default is 2380")
   protected int listenPeerPort = 2380;

   /**
    * It is realted with the --name member flag.
    */
   @Property(doc = "The server name. Each server must have an unique name")
   protected String name;

   /**
    * The values comes from https://coreos.com/etcd/docs/latest/op-guide/configuration.html
    */
   @Property(doc = "Custom configurations passed to Etcd. Default is null", complexConverter = KeyValueProperty.KeyValuePairListConverter.class)
   protected List<KeyValueProperty> configurations;

   public EtcdServerService() {
      if (this.distributionDir == null) {
         this.distributionDir = createTempFolder();
      }
   }

   @Init
   public void init() {
      lifecycle = new EtcdServerLifecycle(this);
   }

   @Override
   protected List<String> getCommand() {

      Map<String, String> memberFlags = new HashMap<>();
      if (name != null) memberFlags.put("--name", this.name);
      memberFlags.put("--initial-advertise-peer-urls", createUrl(this.listenPeerPort));
      memberFlags.put("--listen-peer-urls", createUrl(this.listenPeerPort));
      memberFlags.put("--listen-client-urls", createUrl(this.listenClientPort));
      memberFlags.put("--advertise-client-urls", createUrl(this.listenClientPort));
      memberFlags.put("--discovery", this.discovery);

      // you have a chance to override the default configurations
      if (this.configurations != null) {
         this.configurations.forEach(flag -> memberFlags.put(flag.getKey(), flag.getValue()));
      }

      List<String> arguments = new ArrayList<>(Arrays.asList(distributionDir + "/etcd"));
      memberFlags.forEach((k, v) -> {
         arguments.add(k);
         arguments.add(v);
      });
      return arguments;
   }

   private String createUrl(int port) {
      return String.format("http://%s:%s", this.ip, port);
   }

   private String createTempFolder() {
      File temp;
      try {
         temp = File.createTempFile("etcd", "perf");
         temp.mkdir();
      } catch (IOException e) {
         throw new RuntimeException("Impossible to create a temp folder");
      }
      return temp.getAbsolutePath();
   }

}

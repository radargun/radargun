package org.radargun.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.radargun.Service;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Service(doc = InfinispanServerService.SERVICE_DESCRIPTION)
public class InfinispanServerService extends JavaProcessService {
   protected static final String SERVICE_DESCRIPTION = "Service running Infinispan Server";
   protected static final String JAVA_HOME = "JAVA_HOME";
   protected static final String JAVA_OPTS = "JAVA_OPTS";
   protected static final String JBOSS_HOME = "JBOSS_HOME";

   protected final Log log = LogFactory.getLog(getClass());

   @Property(doc = "Home directory for the server")
   private String home;

   @Property(doc = "Java binary used to start the server. Default is system-default.")
   private String java;

   @Property(doc = "Extra Java options used. Default is none.")
   private String javaOpts;

   @Property(doc = "Directory for storing logs")
   private String logDir;

   @Property(doc = "Is this executed on Windows (should we use *.bat instead of *.sh)? Default is false.")
   private boolean windows = false;

   @Property(name = Service.SLAVE_INDEX, doc = "Index of this slave.")
   private int slaveIndex;

   @Init
   public void init() {
      try {
         URL resource = getClass().getResource("/" + file);
         Path filesystemFile = FileSystems.getDefault().getPath(file);
         Path target = FileSystems.getDefault().getPath(home, "standalone", "configuration", "radargun-" + slaveIndex + ".xml");

         if (resource != null) {
            Files.copy(resource.openStream(), target, StandardCopyOption.REPLACE_EXISTING);
         } else if (filesystemFile.toFile().exists()) {
            Files.copy(filesystemFile, target, StandardCopyOption.REPLACE_EXISTING);
         } else {
            throw new FileNotFoundException("File " + file + " not found neither as resource not in filesystem.");
         }
      } catch (IOException e) {
         log.error("Failed to copy configuration file", e);
         throw new RuntimeException(e);
      }
   }

   @ProvidesTrait
   @Override
   public InfinispanServerLifecycle createLifecycle() {
      return new InfinispanServerLifecycle(this);
   }

   @ProvidesTrait
   public ServerConfigurationProvider createServerConfigurationProvider() {
      return new ServerConfigurationProvider(this);
   }

   @Override
   protected List<String> getCommand() {
      ArrayList<String> command = new ArrayList<String>();
      command.add(FileSystems.getDefault().getPath(home, "bin", "standalone." + (windows ? "bat" : "sh")).toString());
      command.add("-Djboss.node.name=slave" + slaveIndex);
      if (logDir != null) {
         command.add("-Djboss.server.log.dir=" + logDir);
      }
      command.addAll(args);
      command.add("-server-config");
      command.add("radargun-" + slaveIndex + ".xml");
      return command;
   }

   @Override
   public Map<String, String> getEnvironment() {
      Map<String, String> envs = new HashMap(super.getEnvironment());
      if (java != null) {
         if (envs.containsKey(JAVA_HOME)) {
            log.warn("Overwriting " + JAVA_HOME + ": " + envs.get(JAVA_HOME) + " with " + java);
         }
         envs.put(JAVA_HOME, java);
      }
      if (javaOpts != null) {
         if (envs.containsKey(JAVA_OPTS)) {
            log.warn("Overwriting " + JAVA_OPTS + ": " + envs.get(JAVA_OPTS) + " with " + javaOpts);
         }
         envs.put(JAVA_OPTS, javaOpts);
      }
      envs.put(JBOSS_HOME, home);
      return envs;
   }
}

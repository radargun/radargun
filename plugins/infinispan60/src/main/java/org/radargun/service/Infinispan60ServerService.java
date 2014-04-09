package org.radargun.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.infinispan.commons.logging.Log;
import org.infinispan.commons.logging.LogFactory;
import org.radargun.Service;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Service(doc = "Service running Infinispan Server")
public class Infinispan60ServerService extends ProcessService {
   protected static final String RADARGUN_XML = "radargun.xml";
   protected final Log log = LogFactory.getLog(getClass());
   protected static final String JAVA_HOME = "JAVA_HOME";

   @Property(doc = "Home directory for the server")
   private String home;

   @Property(doc = "Java binary used to start the server. Default is system-default.")
   private String java;

   @Property(doc = "Is this executed on Windows (should we use *.bat instead of *.sh)? Default is false.")
   private boolean windows = false;

   @Property(name = Service.SLAVE_INDEX, doc = "Index of this slave.")
   private int slaveIndex;

   @Init
   public void init() {
      try {
         URL resource = getClass().getResource("/" + file);
         Path filesystemFile = FileSystems.getDefault().getPath(file);
         Path target = FileSystems.getDefault().getPath(home, "standalone", "configuration", RADARGUN_XML);

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
   public ProcessLifecycle createLifecycle() {
      return new Infinispan60ServerLifecycle(this);
   }

   @Override
   protected List<String> getCommand() {
      ArrayList<String> command = new ArrayList<String>();
      command.add(FileSystems.getDefault().getPath(home, "bin", "standalone." + (windows ? "bat" : "sh")).toString());
      command.add("-Djboss.node.name=slave" + slaveIndex);
      command.addAll(args);
      command.add("-server-config");
      command.add(RADARGUN_XML);
      return command;
   }

   @Override
   public Map<String, String> getEnvironment() {
      Map<String, String> envs = super.getEnvironment();
      if (java == null) return envs;
      if (envs.containsKey(JAVA_HOME)) {
         log.warn("Overwriting JAVA_HOME " + envs.get(JAVA_HOME) + " with " + java);
      }
      envs.put(JAVA_HOME, java);
      return envs;
   }
}

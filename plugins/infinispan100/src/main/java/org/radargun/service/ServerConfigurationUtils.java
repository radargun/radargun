package org.radargun.service;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

import static org.radargun.service.IncludeConfiguration.*;

/**
 * Utility class to create final server configuration file.
 * @author Gustavo Lira &lt;glira@redhat.com&gt;
 */
public class ServerConfigurationUtils {
   private String radarGunInstalation;
   private static final String CONFIG_FILE_NAME = "/output.xml";
   private static final String SERVER_CONFIG_FOLDER = "/server-configuration";
   protected final Log log = LogFactory.getLog(getClass());
   private String data;

   public ServerConfigurationUtils(String radarGunInstalation) {
      this.radarGunInstalation = radarGunInstalation;
      try {
         Path inputPath = Paths.get(getClass().getResource("/infinispan.xml").toURI());
         this.data = Files.lines(inputPath).collect(Collectors.joining("\n"));
      } catch (IOException | URISyntaxException e) {
         log.error("Can't read infinispan.xml file from resources folder", e);
      }
   }

   /**
    * If jgroupsConfig parameter is provided it will replace the default <jgroups> configuration
    * @param jgroupsConfig
    * @return ServerConfigurationUtils
    */
   public ServerConfigurationUtils jgroupsConfig(String jgroupsConfig) {
      if(jgroupsConfig != null) {
         data = data.replace(JGROUPS_DEFAULT.getValue(), jgroupsConfig);
      }
      return this;
   }

   /**
    * If cacheContainerConfig parameter is provided it will replace the default <cache-container> configuration
    * @param cacheContainerConfig
    * @return ServerConfigurationUtils
    */
   public ServerConfigurationUtils cacheContainerConfig(String cacheContainerConfig) {
      if(cacheContainerConfig != null)
         data =  data.replace(CACHE_CONTAINER_DEFAULT.getValue(), cacheContainerConfig);
      return this;
   }

   /**
    * If interfacesConfig parameter is provided it will replace the default <interfaces> configuration
    * @param interfacesConfig
    * @return ServerConfigurationUtils
    */
   public ServerConfigurationUtils interfacesConfig(String interfacesConfig) {
      if(interfacesConfig != null)
         data = data.replace(INTERFACES_DEFAULT.getValue(), interfacesConfig);
      return this;
   }

   /**
    * If socketBindingsConfig parameter is provided it will replace the default <socket-bindings> configuration
    * @param socketBindingsConfig
    * @return ServerConfigurationUtils
    */
   public ServerConfigurationUtils socketBindingsConfig(String socketBindingsConfig) {
      if(socketBindingsConfig != null) {
         data = data.replace(SOCKET_BINDINGS_DEFAULT.getValue(), socketBindingsConfig);
      }
      return this;
   }

   /**
    * If securityConfig parameter is provided it will replace the default <security> configuration
    * @param securityConfig
    * @return ServerConfigurationUtils
    */
   public ServerConfigurationUtils securityConfig(String securityConfig) {
      if(securityConfig != null)
         data = data.replace(SECURITY_DEFAULT.getValue(), securityConfig);
      return this;
   }

   /**
    * If endpointsConfig parameter is provided it will replace the default <endpoints> configuration
    * @param endpointsConfig
    * @return ServerConfigurationUtils
    */
   public ServerConfigurationUtils endpointsConfig(String endpointsConfig) {
      if(endpointsConfig != null)
            data = data.replace(ENDPOINTS_DEFAULT.getValue(), endpointsConfig);
      return this;
   }

   /**
    * Return the root directory where all the server configuration files are located
    * @return
    */
   public File srcDir() {
      try {
         return Paths.get(getClass().getResource(SERVER_CONFIG_FOLDER).toURI()).toFile();
      } catch (URISyntaxException e) {
         log.error("Failed to retrieve server config folder from src/main/resources", e);
         throw new RuntimeException(e);
      }
   }

   /**
    * Copy the main server configuration and all included files to <home> directory provided on the benchmark file
    * @return
    */
   public void copyFilesToRadargunInstalation() {
      try {
         FileUtils.copyDirectoryToDirectory(srcDir(), new File(radarGunInstalation));
      } catch (IOException e) {
         log.error("Failed to copy server configuration files to: "+ radarGunInstalation, e);
         throw new RuntimeException(e);
      }
   }

   /**
    * Create the final server configuration
    * @return the path where server configuration was written
    */
   public Path writeFile() {
      try {
         log.info(data);
         return Files.write(getOutputPath(), data.getBytes());
      } catch (IOException e) {
         log.error("Failed to write into server configuration. Location: "+getOutputPath(), e);
         throw new RuntimeException(e);
      }
   }

   /**
    * Path where server configuration will be written
    * @return
    */
   public Path getOutputPath() {
      return Paths.get(radarGunInstalation+CONFIG_FILE_NAME);
   }

}

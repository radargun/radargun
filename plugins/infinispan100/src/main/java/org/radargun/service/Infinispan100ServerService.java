package org.radargun.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;

import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.traits.Clustered;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.Utils;

@Service(doc = InfinispanServerService.SERVICE_DESCRIPTION)
public class Infinispan100ServerService extends Infinispan80ServerService {

   @Property(doc = "Default server port for Hotrod, Rest and other operations. Default 11222")
   private Integer defaultServerPort = 11222;

   @Property(doc="jgruoups config. If set, it will replace the default <jgroups> config")
   private String jgroupsConfig;

   @Property(doc="cache-container config. If set, it will replace the default <cache-container> config")
   private String cacheContainerConfig;

   @Property(doc="interfaces config. If set, it will replace the default <interfaces> config")
   private String interfacesConfig;

   @Property(doc="socket bindins config. If set, it will replace the default <socket-bindings> config")
   private String socketBindingsConfig;

   @Property(doc="endpoints config. If set, it will replace the default <endpoints> config")
   private String endpointsConfig;

   @Property(doc="security config. If set, it will replace the default <security> config")
   private String securityConfig;

   @Property(doc="A full path to a logging configuration file. If set, the server will use the provided logging configuration file. If not set, the default server logging configuration is used.")
   private String loggingConfig;

   @Property(doc="Boolean property for enabling user creation on server. If set to true, the new user will be added with provided username and password. Default is false.")
   private boolean addUser=false;

   @Property(doc="Username of the user to be created.")
   private String username;

   @Property(doc="Password of the user to be created.")
   private String password;

   @Property(doc="Libs to be added to the classpath. Build string comma separated. Example: group:artifactId:version;group:artifactId:version")
   private String libs;

   protected Clustered clustered;

   @Override
   public void init() {

      executor = new ScheduledThreadPoolExecutor(executorPoolSize);
      lifecycle = this.createServerLifecyle();

      try {

         clustered = new Infinispan100ServerClustered(this, defaultServerPort, username, password);

         Utils.unzip(getServerZip(), getRadargunInstalationFolder());

         Path homePath = FileSystems.getDefault().getPath(getRadargunInstalationFolder());
         List<Path> homeContents = Files.list(homePath).collect(Collectors.toList());
         // move the server files 1 directory up (remove the extra directory)
         if (homeContents.size() == 1) {
            Path fromDir = homeContents.get(0);
            for (Path path : Files.list(fromDir).collect(Collectors.toList())) {
               Files.move(path, FileSystems.getDefault().getPath(getRadargunInstalationFolder(), path.getFileName().toString()));
            }
            Files.delete(fromDir);
         }
         // the extraction erases the executable bits
         Utils.setPermissions(FileSystems.getDefault().getPath(getRadargunInstalationFolder(), "bin", getStartBashScript()).toString(), "rwxr-xr-x");

         if (addUser) {
            if (username == null || password == null) {
               throw new IllegalArgumentException("Username and Password should be provided for adding user to Server.");
            } else {
               Utils.setPermissions(FileSystems.getDefault().getPath(getRadargunInstalationFolder(), "bin", getCliScriptPrefix() + (getWindows() ? "bat" : "sh")).toString(), "rwxr-xr-x");
               StringBuilder command = new StringBuilder(FileSystems.getDefault().getPath(getRadargunInstalationFolder(), "bin", getCliScriptPrefix() + (getWindows() ? "bat" : "sh")).toString());
               command.append(" user create ").append(username).append(" -p \"").append(password).append("\"");

               log.info(command.toString());
               Process process = Runtime.getRuntime().exec(command.toString());
               process.waitFor();

               log.info("User is created.");
            }
         }

         if (libs != null && !libs.trim().isEmpty()) {
            Path libPath = homePath.resolve("server/lib");
            File[] jars = Maven.resolver().resolve(libs.split(";")).withoutTransitivity().asFile();
            for (File jar : jars) {
               Files.copy(jar.toPath(), libPath.resolve(jar.getName()), StandardCopyOption.REPLACE_EXISTING);
            }
         }

      } catch (IOException e) {
         log.error("Failed to prepare the server directory", e);
         throw new RuntimeException(e);
      } catch (InterruptedException e) {
         log.error("Failed to create user", e);
         throw new RuntimeException(e);
      }

      if (jgroupsDumperEnabled) {
         schedule(new ServerJGroupsDumper(this), jgroupsDumpPeriod);
      }

      topologyHistory = new Infinispan60ServerTopologyHistory(this);
   }

   @Override
   protected String getDefaultStartBashScript() {
      return "server." + (getWindows() ? "bat" : "sh");
   }

   protected String getCliScriptPrefix() {
      return "cli.";
   }

   protected String getUsername() {
      return username;
   }

   protected String getPassword() {
      return password;
   }

   protected Integer getDefaultServerPort() {
      return defaultServerPort;
   }

   @ProvidesTrait
   public Clustered getClustered() {
      return clustered;
   }

   private Path createServerConfiguration() {

      ServerConfigurationUtils configurationBuilder = new ServerConfigurationUtils(getRadargunInstalationFolder())
            .jgroupsConfig(jgroupsConfig)
            .cacheContainerConfig(cacheContainerConfig)
            .interfacesConfig(interfacesConfig)
            .socketBindingsConfig(socketBindingsConfig)
            .endpointsConfig(endpointsConfig)
            .securityConfig(securityConfig);

      Path outputPath = configurationBuilder.writeFile();
      configurationBuilder.copyFilesToRadargunInstalation();
      return outputPath;

   }

   @Override
   protected List<String> getCommand() {
      Path sererConfiguration = createServerConfiguration();
      ArrayList<String> command = new ArrayList<String>();
      command.add(FileSystems.getDefault().getPath(getRadargunInstalationFolder(), "bin", getStartBashScript()).toString());
      command.add("-c");
      command.add(sererConfiguration.toString());
      if (loggingConfig != null) {
         command.add("-l");
         command.add(loggingConfig);
      }
      command.addAll(args);
      return command;
   }

   protected InfinispanServerLifecycle createServerLifecyle() {
      return new Infinispan100ServerLifecycle(this);
   }
}

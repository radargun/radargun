package org.radargun.service;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.traits.Clustered;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.Utils;

import static org.radargun.service.ServerLogPattern.*;

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

   protected Clustered clustered;

   @Override
   public void init() {

      executor = new ScheduledThreadPoolExecutor(executorPoolSize);
      lifecycle = new InfinispanServerLifecycle(this) {

         @Override
         protected Pattern getStartOK() {
            return START_OK.getPattern();
         }

         @Override
         protected Pattern getStartError() {
            return START_ERROR.getPattern();
         }

         @Override
         protected Pattern getStoped() {
            return STOPPED.getPattern();
         }
      };
      clustered = new Infinispan100ServerClustered(this, defaultServerPort);

      try {

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
         Utils.setPermissions(FileSystems.getDefault().getPath(getRadargunInstalationFolder(), "bin", getStartScriptPrefix() + (getWindows() ? "bat" : "sh")).toString(), "rwxr-xr-x");

      } catch (IOException e) {
         log.error("Failed to prepare the server directory", e);
         throw new RuntimeException(e);
      }

      if (jgroupsDumperEnabled) {
         schedule(new ServerJGroupsDumper(this), jgroupsDumpPeriod);
      }

   }

   @Override
   protected String getStartScriptPrefix() {
      return "server.";
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
      command.add(FileSystems.getDefault().getPath(getRadargunInstalationFolder(), "bin", getStartScriptPrefix() + (getWindows() ? "bat" : "sh")).toString());
      command.add("-c");
      command.add(sererConfiguration.toString());
      command.addAll(args);
      return command;
   }

}

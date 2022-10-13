package org.radargun.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.management.MBeanServerConnection;

import org.radargun.Service;
import org.radargun.ServiceHelper;
import org.radargun.config.Destroy;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.Utils;

/**
 * A service for starting EAP 7.x server.
 *
 * The username and password properties must be set so the Tomcat
 * service can verify that the server is properly running.
 * The server is considered properly running once the query for
 * the following address returns "OK" in the response body:
 *    http://bindAddress:bindHttpPort/manager/text/list
 *
 * The conf/tomcat-users.xml file within Tomcat must include a user
 * with "manager-script" role and corresponding credentials.
 *
 * @author Martin Gencur
 */
@Service(doc = EAPServerService.SERVICE_DESCRIPTION)
public class EAPServerService extends JavaProcessService {

   protected static final String SERVICE_DESCRIPTION = "Service running EAP Server";
   protected static final String JBOSS_HOME = "JBOSS_HOME";

   protected final Log log = LogFactory.getLog(getClass());

   @Property(doc = "Home directory for the server")
   private String home;

   @Property(doc = "Server zip. If set, the zip will be extracted to the 'home' directory.")
   private String serverZip;

   @Property(doc = "Period for checking current membership. Default is 10 seconds.", converter = TimeConverter.class)
   protected long viewCheckPeriod = 10000;

   @Property(doc = "Number of threads in scheduled tasks pool. Default is 2.")
   protected int executorPoolSize = 2;

   @Property(doc = "Path to file which should be deployed to EAP server.", optional = true)
   private String deploymentWarPath;

   protected ScheduledExecutorService executor;

   protected volatile MBeanServerConnection connection;

   @Init
   public void init() {
      executor = new ScheduledThreadPoolExecutor(executorPoolSize);
      lifecycle = new EAPServerLifecycle(this);

      try {
         if (serverZip != null) {
            Utils.unzip(serverZip, home);

            Path homePath = FileSystems.getDefault().getPath(home);
            List<Path> homeContents = Files.list(homePath).collect(Collectors.toList());
            // move the server files 1 directory up (remove the extra directory)
            if (homeContents.size() == 1) {
               Path fromDir = homeContents.get(0);
               for (Path path : Files.list(fromDir).collect(Collectors.toList())) {
                  Files.move(path, FileSystems.getDefault().getPath(home, path.getFileName().toString()));
               }
               Files.delete(fromDir);
            }
            // the extraction erases the executable bits
            Utils.setPermissions(FileSystems.getDefault().getPath(home, "bin", getStartScriptPrefix() + "sh").toString(), "rwxr-xr-x");
         }

         evaluateFile(home, "standalone", "configuration");

         //If war file path is provided for deploying to EAP server, copy it to deployment forlder.
         if (deploymentWarPath != null) {
            Path src = Paths.get(deploymentWarPath);
            Path dest = Paths.get(home + "/standalone/deployments/" + new File(deploymentWarPath).getName());
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
         }

      } catch (IOException e) {
         log.error("Failed to prepare the server directory", e);
         throw new RuntimeException(e);
      }
   }

   @Destroy
   public void destroy() {
      Utils.shutdownAndWait(executor);
   }

   @ProvidesTrait
   public EAPConfigurationProvider createServerConfigurationProvider() {
      return new EAPConfigurationProvider(this);
   }

   @Override
   protected List<String> getCommand() {
      ArrayList<String> command = new ArrayList<String>();
      command.add(FileSystems.getDefault().getPath(home, "bin", getStartScriptPrefix() + "sh").toString());
      command.add("-Djboss.node.name=worker" + ServiceHelper.getContext().getWorkerIndex());

      command.addAll(args);
      command.add("-server-config");
      command.add("radargun-" + ServiceHelper.getContext().getWorkerIndex() + ".xml");
      return command;
   }

   protected String getStartScriptPrefix() {
      return "standalone.";
   }

   @Override
   public Map<String, String> getEnvironment() {
      Map<String, String> envs = super.getEnvironment();
      envs.put(JBOSS_HOME, home);
      return envs;
   }

   protected void schedule(Runnable task, long period) {
      executor.scheduleAtFixedRate(() -> {
         try {
            task.run();
         } catch (Exception e) {
            log.error("Error while executing Infinispan Server Task", e);
         }
      }, 0, period, TimeUnit.MILLISECONDS);
   }

   protected String getRadargunInstalationFolder() {
      return home;
   }

   protected String getServerZip() {
      return serverZip;
   }
}

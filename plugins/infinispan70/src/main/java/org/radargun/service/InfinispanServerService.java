package org.radargun.service;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;

import org.radargun.Service;
import org.radargun.ServiceHelper;
import org.radargun.config.Destroy;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Clustered;
import org.radargun.traits.JmxConnectionProvider;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.Utils;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Service(doc = InfinispanServerService.SERVICE_DESCRIPTION)
public class InfinispanServerService extends JavaProcessService {
   protected static final String SERVICE_DESCRIPTION = "Service running Infinispan Server";
   protected static final String JBOSS_HOME = "JBOSS_HOME";

   protected final Log log = LogFactory.getLog(getClass());

   @Property(doc = "Home directory for the server")
   private String home;

   @Property(doc = "Server zip. If set, the zip will be extracted to the 'home' directory.")
   private String serverZip;

   @Property(doc = "Directory for storing logs")
   private String logDir;

   @Property(doc = "Is this executed on Windows (should we use *.bat instead of *.sh)? Default is false.")
   private boolean windows = false;

   @Deprecated
   @Property(doc = "JMX domain. Default is 'org.infinispan'. Deprecated: The CacheManager can be retrieved by type instead.")
   protected String jmxDomain = "org.infinispan";

   @Deprecated
   @Property(doc = "Name of the cache manager/cache container. Default is 'default'. Deprecated: The CacheManager can be retrieved by type instead.")
   protected String cacheManagerName = "default";

   @Property(doc = "Start thread periodically dumping JGroups JMX data. Default is false.")
   protected boolean jgroupsDumperEnabled = false;

   @Property(doc = "Period in which should be JGroups JMX data dumped. Default is 10 seconds.", converter = TimeConverter.class)
   protected long jgroupsDumpPeriod = 10000;

   @Property(doc = "Period for checking current membership. Default is 10 seconds.", converter = TimeConverter.class)
   protected long viewCheckPeriod = 10000;

   @Property(doc = "Number of threads in scheduled tasks pool. Default is 2.")
   protected int executorPoolSize = 2;

   @Property(doc = "The executable bash script responsible to start the server")
   protected String startBashScript;

   protected ScheduledExecutorService executor;
   protected Clustered clustered;

   protected volatile MBeanServerConnection connection;

   @Init
   public void init() {
      executor = new ScheduledThreadPoolExecutor(executorPoolSize);
      lifecycle = new InfinispanServerLifecycle(this);
      clustered = new InfinispanServerClustered(this);

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
            Utils.setPermissions(FileSystems.getDefault().getPath(home, "bin", getStartBashScript()).toString(), "rwxr-xr-x");
         }

         evaluateFile(home, "standalone", "configuration");

      } catch (IOException e) {
         log.error("Failed to prepare the server directory", e);
         throw new RuntimeException(e);
      }
      lifecycle.addListener(new ProcessLifecycle.ListenerAdapter() {
         private JMXConnector connector;

         @Override
         public void afterStart() {
            JmxConnectionProvider connectionProvider = createConnectionProvider();
            if (connectionProvider == null) {
               return;
            }
            connector = connectionProvider.getConnector();
            try {
               connection = connector.getMBeanServerConnection();
            } catch (IOException e) {
               log.error("Failed to open MBean connection", e);
            }
         }

         @Override
         public void beforeStop(boolean graceful) {
            connection = null;
            try {
               if (connector != null) connector.close();
            } catch (IOException e) {
               log.error("Failed to close JMX connector", e);
            }
         }
      });
      if (jgroupsDumperEnabled) {
         schedule(new ServerJGroupsDumper(this), jgroupsDumpPeriod);
      }
   }

   @Destroy
   public void destroy() {
      Utils.shutdownAndWait(executor);
   }

   @ProvidesTrait
   public ServerConfigurationProvider createServerConfigurationProvider() {
      return new ServerConfigurationProvider(this);
   }

   @ProvidesTrait
   public Clustered getClustered() {
      return clustered;
   }

   @Override
   protected List<String> getCommand() {
      ArrayList<String> command = new ArrayList<String>();
      command.add(FileSystems.getDefault().getPath(home, "bin", getStartBashScript()).toString());
      command.add("-Djboss.node.name=worker" + ServiceHelper.getContext().getWorkerIndex());
      if (logDir != null) {
         command.add("-Djboss.server.log.dir=" + logDir);
      }
      command.addAll(args);
      command.add("-server-config");
      command.add("radargun-" + ServiceHelper.getContext().getWorkerIndex() + ".xml");
      return command;
   }

   protected String getStartBashScript() {
      if (startBashScript != null) {
         return startBashScript;
      } else {
         return getDefaultStartBashScript();
      }
   }

   protected String getDefaultStartBashScript() {
      return "clustered." + (windows ? "bat" : "sh");
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

   protected boolean getWindows() {
      return windows;
   }
}

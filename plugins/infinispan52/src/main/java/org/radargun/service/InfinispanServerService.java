package org.radargun.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;

import org.radargun.Service;
import org.radargun.ServiceHelper;
import org.radargun.config.Destroy;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
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

   @Property(doc = "JMX domain. Default is 'org.infinispan'.")
   protected String jmxDomain = "org.infinispan";

   @Property(doc = "Name of the cache manager/cache container. Default is 'default'.")
   protected String cacheManagerName = "default";

   @Property(doc = "Start thread periodically dumping JGroups JMX data. Default is false.")
   protected boolean jgroupsDumperEnabled = false;

   @Property(doc = "Period in which should be JGroups JMX data dumped. Default is 10 seconds.", converter = TimeConverter.class)
   protected long jgroupsDumpPeriod = 10000;

   @Property(doc = "Period for checking current membership. Default is 10 seconds.", converter = TimeConverter.class)
   protected long viewCheckPeriod = 10000;

   @Property(doc = "Number of threads in scheduled tasks pool. Default is 2.")
   protected int executorPoolSize = 2;

   protected ScheduledExecutorService executor;

   protected InfinispanServerLifecycle lifecycle;
   protected InfinispanServerClustered clustered;

   protected volatile MBeanServerConnection connection;

   @Init
   public void init() {
      executor = new ScheduledThreadPoolExecutor(executorPoolSize);
      lifecycle = new InfinispanServerLifecycle(this);
      clustered = new InfinispanServerClustered(this);

      try {
         URL resource = getClass().getResource("/" + file);
         Path filesystemFile = FileSystems.getDefault().getPath(file);
         Path target = FileSystems.getDefault().getPath(home, "standalone", "configuration", "radargun-" + ServiceHelper.getSlaveIndex() + ".xml");

         if (resource != null) {
            try (InputStream is = resource.openStream()) {
               Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            }
         } else if (filesystemFile.toFile().exists()) {
            Files.copy(filesystemFile, target, StandardCopyOption.REPLACE_EXISTING);
         } else {
            throw new FileNotFoundException("File " + file + " not found neither as resource not in filesystem.");
         }
      } catch (IOException e) {
         log.error("Failed to copy configuration file", e);
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
   @Override
   public InfinispanServerLifecycle createLifecycle() {
      return lifecycle;
   }

   @ProvidesTrait
   public ServerConfigurationProvider createServerConfigurationProvider() {
      return new ServerConfigurationProvider(this);
   }

   @ProvidesTrait
   public InfinispanServerClustered getClustered() {
      return clustered;
   }

   @Override
   protected List<String> getCommand() {
      ArrayList<String> command = new ArrayList<String>();
      command.add(FileSystems.getDefault().getPath(home, "bin", getStartScriptPrefix() + (windows ? "bat" : "sh")).toString());
      command.add("-Djboss.node.name=slave" + ServiceHelper.getSlaveIndex());
      if (logDir != null) {
         command.add("-Djboss.server.log.dir=" + logDir);
      }
      command.addAll(args);
      command.add("-server-config");
      command.add("radargun-" + ServiceHelper.getSlaveIndex() + ".xml");
      return command;
   }

   protected String getStartScriptPrefix() {
      return "clustered.";
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

   protected void schedule(Runnable task, long period) {
      executor.scheduleAtFixedRate(task, 0, period, TimeUnit.MILLISECONDS);
   }
}

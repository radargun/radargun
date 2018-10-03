package org.radargun.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import org.radargun.config.FlightRecorder;
import org.radargun.config.Property;
import org.radargun.config.PropertyDelegate;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.JmxConnectionProvider;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class JavaProcessService extends ProcessService {
   private final Log log = LogFactory.getLog(getClass());

   private static final String CONNECTOR_ADDRESS =
         "com.sun.management.jmxremote.localConnectorAddress";

   protected static final String JAVA_HOME = "JAVA_HOME";
   protected static final String JAVA_OPTS = "JAVA_OPTS";

   @Property(doc = "Java binary used to start the server. Default is ${env.JAVA_HOME}.")
   protected String java = System.getenv(JAVA_HOME);

   @Property(doc = "Extra Java options used. Default is none.")
   protected String javaOpts;

   @Property(doc = "Connect to the process and retrieve JMX connection. Default is true.")
   protected boolean jmxConnectionEnabled = true;

   @PropertyDelegate(prefix = "flight-recorder.")
   private FlightRecorder flightRecorder = new FlightRecorder();

   @ProvidesTrait
   public JmxConnectionProvider createConnectionProvider() {
      try {
         Class.forName("com.sun.tools.attach.AttachNotSupportedException");
      } catch (ClassNotFoundException e) {
         log.warn("Cannot load JDK classes from JAVA_HOME/lib/tools.jar. " +
                  "Please make sure that tools.jar is on the classpath by " +
                  "adding the following entry to plugin.properties:\n\n" +
                  "classpath ${env.JAVA_HOME}/lib/tools.jar\n");
         String classPath = System.getProperty("java.class.path");
         if (classPath.contains("tools.jar")) {
            Optional<String> tools = Arrays.asList(classPath.split(File.pathSeparator)).stream().filter(s -> s.contains("tools.jar")).findFirst();
            if (tools.isPresent()) {
               if (new File(tools.get()).exists()) {
                  log.warnf("Classpath contains tools.jar (%s), that file exists but the classes are not there.", tools.get());
               } else {
                  log.warnf("Classpath contains tools.jar (%s) but the file does not exist", tools.get());
               }
            } else {
               log.warn("Classpath is " + classPath);
            }
         }
         return null;
      }
      if (!jmxConnectionEnabled) {
         log.info("Connecting to the service via JMX is disabled.");
         return null;
      }
      return new JmxConnectionProvider() {
         @Override
         public JMXConnector getConnector() {
            String pid = getJavaPIDs();
            if (pid == null) return null;
            try {
               VirtualMachine vm;
               try {
                  vm = VirtualMachine.attach(pid);
               } catch (AttachNotSupportedException e) {
                  log.error("Cannot attach to machine " + pid, e);
                  return null;
               }
               String connectorAddress = vm.getAgentProperties().getProperty(CONNECTOR_ADDRESS);
               if (connectorAddress == null) {
                  String agent = vm.getSystemProperties().getProperty("java.home") +
                        File.separator + "lib" + File.separator +
                        "management-agent.jar";
                  try {
                     vm.loadAgent(agent);
                     connectorAddress = vm.getAgentProperties().getProperty(CONNECTOR_ADDRESS);
                  } catch (Exception e) {
                     log.error("Cannot load management agent into target VM.");
                  }
               }
               if (connectorAddress == null) {
                  log.error("Failed to retrieve connector address.");
                  return null;
               }
               return JMXConnectorFactory.connect(new JMXServiceURL(connectorAddress));
            } catch (NumberFormatException e) {
               log.error("Failed to parse service JVM PID");
               return null;
            } catch (IOException e) {
               log.error("Failed to connect JVM", e);
               return null;
            }
         }
      };
   }

   public String getJavaPIDs() {

      String currentPid = lifecycle.getPid();

      if (currentPid != null) {
         ProcessBuilder pb = new ProcessBuilder()
               .command(Arrays.asList(getCommandPrefix() + "jvms" + getCommandSuffix(), currentPid));
         pb.redirectError(ProcessBuilder.Redirect.INHERIT);
         pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
         try {
            Process process = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
               String line;
               while ((line = reader.readLine()) != null)
                  sb.append(line);
            }

            log.info("All Java PIDs: '" + sb.toString() + "'");

            currentPid = sb.toString().split(" ")[0];
         } catch (IOException e) {
            log.error("Failed to read JVM PIDs", e);
         }
      }

      log.info("Java PIDs: '" + currentPid + "'");

      return currentPid;
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
      if (javaOpts != null && !javaOpts.trim().isEmpty()) {
         if (envs.containsKey(JAVA_OPTS)) {
            log.warn("Overwriting " + JAVA_OPTS + ": " + envs.get(JAVA_OPTS) + " with " + javaOpts);
         }
         envs.put(JAVA_OPTS, javaOpts);
      // we should keep the environment variable for later reuse
      } else if (System.getenv(JAVA_OPTS) != null) {
         envs.put(JAVA_OPTS, System.getenv(JAVA_OPTS));
      }
      // the default implementation is using setArgs. I would like to avoid doing this refactor
      if (flightRecorder.isEnabled()) {
         List<String> flightRecorderArgs = new ArrayList<>(1);
         flightRecorder.setArgs(flightRecorderArgs);
         StringBuilder flightRecorderArgBuilder = new StringBuilder();
         flightRecorderArgs.forEach(arg -> flightRecorderArgBuilder.append(arg).append(" "));
         appendJavaOpts(envs, flightRecorderArgBuilder);
      }
      return envs;
   }

   private void appendJavaOpts(Map<String, String> envs, StringBuilder arguments) {
      if (envs.get(JAVA_OPTS) != null) {
         arguments.append(" ").append(envs.get(JAVA_OPTS));
      }
      envs.put(JAVA_OPTS, arguments.toString());
   }
}

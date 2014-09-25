package org.radargun.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
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


   @ProvidesTrait
   public JmxConnectionProvider createConnectionProvider() {
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
      ProcessBuilder pb = new ProcessBuilder().command(Arrays.asList(getCommandPrefix() + "jvms" + getCommandSuffix(), tag));
      pb.redirectError(ProcessBuilder.Redirect.INHERIT);
      pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
      try {
         Process process = pb.start();
         StringBuilder sb = new StringBuilder();
         try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
         }
         return sb.toString().trim();
      } catch (IOException e) {
         log.error("Failed to read JVM PIDs", e);
         return null;
      }
   }
}

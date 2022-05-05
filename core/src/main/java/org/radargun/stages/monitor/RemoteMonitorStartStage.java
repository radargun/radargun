package org.radargun.stages.monitor;

import java.io.IOException;
import java.net.MalformedURLException;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.traits.InternalsExposition;
import org.radargun.traits.JmxConnectionProvider;

@Stage(doc = "Starts collecting statistics locally on main and each worker node.", deprecatedName = "jvm-monitor-start")
public class RemoteMonitorStartStage extends AbstractMonitorStartStage {

   private JmxConnectionProvider jmxConnectionProvider;

   @Property(doc = "Remote JMX service url. Example: service:jmx:rmi:///jndi/rmi://127.0.0.1:9999/jmxrmi")
   private String jmxServiceUrl;

   @Override
   protected JmxConnectionProvider getJmxConnectionProvider() {
      if (jmxConnectionProvider == null) {
         jmxConnectionProvider = () -> {
            JMXServiceURL url;
            try {
               url = new JMXServiceURL(jmxServiceUrl);
            } catch (MalformedURLException e) {
               throw new IllegalStateException(e);
            }
            JMXConnector jmxConnector;
            try {
               jmxConnector = JMXConnectorFactory.connect(url);
            } catch (IOException e) {
               throw new IllegalStateException(e);
            }
            return jmxConnector;
         };
      }
      return jmxConnectionProvider;
   }

   @Override
   protected InternalsExposition getInternalsExposition() {
      return null;
   }
}

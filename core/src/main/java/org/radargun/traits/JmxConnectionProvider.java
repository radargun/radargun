package org.radargun.traits;

import javax.management.remote.JMXConnector;

@Trait(doc = "Exposes JMX connection to the service. If this trait it not provided, " +
   "it's expected that the service is entirely hosted in current JVM.")
public interface JmxConnectionProvider {
   /**
    * @return The connector, already connected to the service.
    */
   JMXConnector getConnector();
}

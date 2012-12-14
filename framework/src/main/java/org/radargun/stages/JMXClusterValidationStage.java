package org.radargun.stages;

import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.config.TimeConverter;
import org.radargun.jmx.JMXClusterValidator;
import org.radargun.utils.Utils;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates formation of the cluster remotely via JMX
 * 
 * @author Michal Linhard <mlinhard@redhat.com>
 */
@Stage(doc = "Validates formation of the cluster remotely via JMX.")
public class JMXClusterValidationStage extends AbstractMasterStage {

   @Property(doc = "Indices of slaves that should be up. Default is empty.")
   protected List<Integer> slaves;

   @Property(converter = TimeConverter.class, doc = "JMX connection timeout. Default is 3 seconds.")
   private long jmxConnectionTimeout = 3000;

   @Property(converter = TimeConverter.class, doc = "Cluster validation timeout. Default is 1 minute.")
   private long waitTimeout = 60000;

   @Property(doc = "Generic property 1.")
   private String prop1;

   @Property(doc = "Generic property 1.")
   private String prop2;

   @Property(doc = "Generic property 1.")
   private String prop3;

   public JMXClusterValidationStage() {
      // nada
   }

   @Override
   public boolean execute() throws Exception {
      try {
         String validatorClass = Utils.getCacheProviderProperty(masterState.nameOfTheCurrentBenchmark(),
               "org.radargun.jmx.clustervalidator");
         if (validatorClass == null) {
            throw new IllegalStateException(
                  "There's no org.radargun.jmx.clustervalidator defined in cacheprovider.properties");
         }
         
         jmxConnectionTimeout = (Long) masterState.get(JMXClusterValidationPrepareStage.STATE_JMX_CONN_TIMEOUT);
         waitTimeout = (Long) masterState.get(JMXClusterValidationPrepareStage.STATE_WAIT_TIMEOUT);
         @SuppressWarnings("unchecked")
         List<InetSocketAddress> slaveJMXEndpoints = (List<InetSocketAddress>) masterState.get(JMXClusterValidationPrepareStage.STATE_SLAVE_JMX_ENDPOINTS);
         prop1 = masterState.getString(JMXClusterValidationPrepareStage.STATE_PROP1);
         prop2 = masterState.getString(JMXClusterValidationPrepareStage.STATE_PROP2);
         prop3 = masterState.getString(JMXClusterValidationPrepareStage.STATE_PROP3);
         
         List<InetSocketAddress> filteredSlaveJMXEndpoints = slaveJMXEndpoints;
         if (slaves != null && !slaves.isEmpty()) {
            filteredSlaveJMXEndpoints = new ArrayList<InetSocketAddress>();
            for (Integer slaveIdx : slaves ){
               filteredSlaveJMXEndpoints.add(slaveJMXEndpoints.get(slaveIdx));
            }
         }
         
         JMXClusterValidator validator = (JMXClusterValidator) createInstance(validatorClass);
         validator.init(filteredSlaveJMXEndpoints, jmxConnectionTimeout, prop1, prop2, prop3);
         log.info("Waiting for cluster formation ...");
         return validator.waitUntilClusterFormed(waitTimeout);
      } catch (Exception e) {
         log.error("Error while validating cluster", e);
         return false;
      }
   }
}

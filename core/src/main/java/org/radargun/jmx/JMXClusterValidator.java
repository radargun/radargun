package org.radargun.jmx;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Validates cluster formation via JMX
 * 
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 */
public interface JMXClusterValidator {
   /**
    * 
    * Initialises the validator instance.
    * 
    * @param slaveAddresses
    *           All slave addresses
    * @param jmxConnectionTimeout
    *           Timeout for remote JMX connections
    * @param prop1
    *           Optional config property for JMXClusterValidator implementation
    * @param prop2
    *           Optional config property for JMXClusterValidator implementation
    * @param prop3
    *           Optional config property for JMXClusterValidator implementation
    */
   void init(List<InetSocketAddress> slaveAddresses, long jmxConnectionTimeout, String prop1, String prop2, String prop3);

   /**
    * 
    * Block until the cluster is formed.
    * 
    * @param slaveIndices
    *           Indices of slaves that we want to form the cluster.
    * @param timeout
    *           Wait timeout
    * @return true - cluster has successfully formed, false - wait was timed out or error occured
    */
   boolean waitUntilClusterFormed(long timeout);
}

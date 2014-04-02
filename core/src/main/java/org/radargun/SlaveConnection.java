package org.radargun;

import java.io.IOException;
import java.util.List;

import org.radargun.config.Cluster;
import org.radargun.config.Configuration;
import org.radargun.config.Scenario;
import org.radargun.reporting.Timeline;

/**
 * Facade for master node to communicate with local or remote slaves
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface SlaveConnection {
   /**
    * Opens the connection to all slaves, retrieves requested slave index
    * and sends final slave index and total slave count.
    * @throws IOException
    */
   void establish() throws IOException;

   /**
    * Sends the description of scenario to all slaves.
    *
    * @param scenario
    * @throws IOException
    */
   void sendScenario(Scenario scenario) throws IOException;

   /**
    * Sends the configuration (setups) that will be used for the benchmark to all slaves.
    * @param configuration
    * @throws IOException
    */
   void sendConfiguration(Configuration configuration) throws IOException;

   /**
    * Sends the cluster description that will be used for the benchmark to all slaves.
    * @param cluster
    * @throws IOException
    */
   void sendCluster(Cluster cluster) throws IOException;

   /**
    * Signalizes to numSlaves slaves that these should run the stage from previously send scenario.
    * @param stageId
    * @param numSlaves
    * @return Acknowledgement from each of the numSlaves slaves.
    * @throws IOException
    */
   List<DistStageAck> runStage(int stageId, int numSlaves) throws IOException;

   /**
    * Retrieves timeline for the passed benchmark from numSlaves slaves.
    * @see Timeline
    *
    * @param numSlaves
    * @return
    * @throws IOException
    */
   List<Timeline> receiveTimelines(int numSlaves) throws IOException;

   /**
    * Close the connection to slaves, releasing all resources.
    */
   void release();
}

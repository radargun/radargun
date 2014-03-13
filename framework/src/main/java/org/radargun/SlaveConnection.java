package org.radargun;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.radargun.config.Cluster;
import org.radargun.config.Configuration;
import org.radargun.config.Scenario;
import org.radargun.reporting.Report;
import org.radargun.reporting.Timeline;

/**
 * Facade to communicate with local or remote slaves
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface SlaveConnection {
   void establish() throws IOException;
   void sendScenario(Scenario scenario) throws IOException;
   void sendConfiguration(Configuration configuration) throws IOException;
   void sendCluster(Cluster cluster) throws IOException;
   List<DistStageAck> runStage(int stageId, int numSlaves) throws IOException;
   List<Timeline> receiveTimelines(int numSlaves) throws IOException;
   void release();
}

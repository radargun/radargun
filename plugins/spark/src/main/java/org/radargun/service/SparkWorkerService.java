package org.radargun.service;

import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.List;

import org.radargun.Service;
import org.radargun.config.Init;
import org.radargun.config.Property;

/**
 * @author Matej Cimbora
 */
@Service(doc = "Service encapsulating Apache Spark (worker node). Current implementation is limited to one slave per host.")
public class SparkWorkerService extends AbstractSparkService {

   @Property(doc = "Name of the host where master node is deployed. Default is localhost.")
   protected String host = "localhost";

   @Property(doc = "Port under which master node is accessible. Default is 7077.")
   protected int port = 7077;

   @Init
   public void init() {
      lifecycle = new SparkWorkerLifecycle(this);
   }

   @Override
   protected List<String> getCommand() {
      ArrayList<String> command = new ArrayList<String>();
      command.add(FileSystems.getDefault().getPath(home, "sbin", "start-slave.sh").toString());
      command.add("spark://" + host + ":" + port);
      return command;
   }
   
   
   protected String getPidFileIdent() {
      return "radargun_worker_1";
   }
}

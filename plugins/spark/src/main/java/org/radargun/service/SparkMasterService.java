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
@Service(doc = "Service encapsulating Apache Spark (master node).")
public class SparkMasterService extends AbstractSparkService {

   @Property(doc = "Name of the host where master node will be deployed. Default is localhost.")
   protected String host = "localhost";

   @Property(doc = "Port under which master node is to be accessible. Default is 7077.")
   protected int port = 7077;

   @Init
   public void init() {
      this.lifecycle = new SparkMasterLifecycle(this);
   }


   @Override
   protected List<String> getCommand() {
      ArrayList<String> command = new ArrayList<String>();
      command.add(FileSystems.getDefault().getPath(home, "sbin", "start-master.sh").toString());
      command.add(" -h " + host + " -p " + port);
      return command;
   }

   protected String getPidFileIdent() {
      return "radargun_master_" + this.port;
   }
}

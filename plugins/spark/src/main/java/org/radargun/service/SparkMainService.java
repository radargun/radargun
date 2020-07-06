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
@Service(doc = "Service encapsulating Apache Spark (main node).")
public class SparkMainService extends AbstractSparkService {

   @Property(doc = "Name of the host where main node will be deployed. Default is localhost.")
   protected String host = "localhost";

   @Property(doc = "Port under which main node is to be accessible. Default is 7077.")
   protected int port = 7077;

   @Init
   public void init() {
      this.lifecycle = new SparkMainLifecycle(this);
   }


   @Override
   protected List<String> getCommand() {
      ArrayList<String> command = new ArrayList<String>();
      command.add(FileSystems.getDefault().getPath(home, "sbin", "start-main.sh").toString());
      command.add(" -h " + host + " -p " + port);
      return command;
   }

   protected String getPidFileIdent() {
      return "radargun_main_" + this.port;
   }
}

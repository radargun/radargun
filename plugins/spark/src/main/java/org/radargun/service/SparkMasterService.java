package org.radargun.service;

import org.radargun.Service;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.traits.ProvidesTrait;

import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Matej Cimbora
 */
@Service(doc = "Service encapsulating Apache Spark (master node).")
public class SparkMasterService extends AbstractSparkService {

   private SparkMasterLifecycle lifecycle;

   @Init
   public void init() {
      this.lifecycle = new SparkMasterLifecycle(this);
   }

   @Override
   @ProvidesTrait
   public ProcessLifecycle createLifecycle() {
      return lifecycle;
   }

   @Override
   protected List<String> getCommand() {
      ArrayList<String> command = new ArrayList<String>();
      command.add(FileSystems.getDefault().getPath(home, "sbin", "start-master.sh").toString());
      return command;
   }
}

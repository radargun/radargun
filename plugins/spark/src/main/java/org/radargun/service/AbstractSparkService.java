package org.radargun.service;

import java.nio.file.FileSystems;
import java.util.Map;

import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.utils.TimeConverter;

/**
 * @author Matej Cimbora
 */
@Service(doc = "Parent for Master & Worker services")
public abstract class AbstractSparkService extends JavaProcessService {

   @Property(doc = "Home directory of the Spark distribution.", optional = false)
   protected String home;

   @Property(doc = "Maximum time to wait until Spark's log file gets created after service was started. " +
         "Default is 30 seconds.", converter = TimeConverter.class)
   protected long logCreationTimeout = 30000;
   
   @Property(doc = "Maximum time to wait until Spark's startup process finishes. " +
         "Default is 5 seconds.", converter = TimeConverter.class)
   protected long startupProcessTimeout = 5000;

   @Override
   public Map<String, String> getEnvironment() {
      Map<String, String> envMap = super.getEnvironment();
      envMap.put("SPARK_PID_DIR", FileSystems.getDefault().getPath(home, "pids").toString());
      envMap.put("SPARK_IDENT_STRING", getPidFileIdent());
      return envMap;
   }

   protected abstract String getPidFileIdent();
}

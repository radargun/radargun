package org.radargun.service;

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

}

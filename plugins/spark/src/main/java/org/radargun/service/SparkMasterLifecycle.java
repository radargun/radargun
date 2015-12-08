package org.radargun.service;

import java.util.regex.Pattern;

/**
 * @author Matej Cimbora
 */
public class SparkMasterLifecycle extends AbstractSparkLifecycle {

   private static final Pattern START_PATTERN = Pattern.compile(".*starting org\\.apache\\.spark\\.deploy\\.master\\.Master, logging to.*");

   public SparkMasterLifecycle(SparkMasterService service) {
      super(service);
   }

   @Override
   protected Pattern getStartPattern() {
      return START_PATTERN;
   }
}

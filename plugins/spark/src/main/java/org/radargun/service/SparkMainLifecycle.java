package org.radargun.service;

import java.util.regex.Pattern;

/**
 * @author Matej Cimbora
 */
public class SparkMainLifecycle extends AbstractSparkLifecycle {

   private static final Pattern START_PATTERN = Pattern.compile(".*starting org\\.apache\\.spark\\.deploy\\.main\\.Main, logging to.*");

   public SparkMainLifecycle(SparkMainService service) {
      super(service);
   }

   @Override
   protected Pattern getStartPattern() {
      return START_PATTERN;
   }
}

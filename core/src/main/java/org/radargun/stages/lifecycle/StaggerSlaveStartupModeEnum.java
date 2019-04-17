package org.radargun.stages.lifecycle;

import org.radargun.config.DocumentedValue;

public enum StaggerSlaveStartupModeEnum {
   @DocumentedValue("The slaves will be started all in once.")
   SYNC,
   @DocumentedValue("The slave startup can be delayed.")
   ASYNC
}

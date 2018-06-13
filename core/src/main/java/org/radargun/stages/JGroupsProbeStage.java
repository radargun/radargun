package org.radargun.stages;

import org.radargun.DistStageAck;
import org.radargun.config.Stage;

/**
 * A stage that execute probe once.
 *
 * @author Matej Cimbora
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
@Stage(doc = "Allows to invoke JGroups probe queries. For details on probe usage see org.jgroups.tests.Probe.")
public class JGroupsProbeStage extends AbstractJGroupsProbeStage {

   @Override
   public DistStageAck executeOnSlave() {
      return run();
   }
}

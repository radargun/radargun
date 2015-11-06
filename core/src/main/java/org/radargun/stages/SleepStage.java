package org.radargun.stages;


import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.utils.TimeConverter;

/**
 * Sleeps specified number of milliseconds.
 *
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 */
@Stage(doc = "Sleeps specified number of milliseconds.")
public class SleepStage extends AbstractMasterStage {

   @Property(optional = false, converter = TimeConverter.class, doc = "Sleep duration.")
   public long time;

   public StageResult execute() {
      log.trace("Sleeping " + time + " ms");
      try {
         Thread.sleep(time);
         return StageResult.SUCCESS;
      } catch (InterruptedException e) {
         log.warn("Sleep interrupted", e);
         return errorResult();
      }
   }
}

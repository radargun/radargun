package org.radargun.stages;


import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.config.TimeConverter;

/**
 * Sleeps specified number of milliseconds.
 * 
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 */
@Stage(doc = "Sleeps specified number of milliseconds.")
public class SleepStage extends AbstractMasterStage {

   @Property(optional = false, converter = TimeConverter.class, doc = "Sleep duration.")
   private long time;

   public void setTime(long time) {
      this.time = time;
   }

   public boolean execute() {
      log.trace("Sleeping " + time + " ms");
      try {
         Thread.sleep(time);
         return true;
      } catch (InterruptedException e) {
         log.warn("Sleep interrupted", e);
         return false;
      }
   }

   @Override
   public String toString() {
      return "SleepStage {time=" + time + "}";
   }
}

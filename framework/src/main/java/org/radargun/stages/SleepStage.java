package org.radargun.stages;


/**
 * Sleeps specified number of milliseconds.
 * 
 * @author Michal Linhard <mlinhard@redhat.com>
 */
public class SleepStage extends AbstractMasterStage {

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

}

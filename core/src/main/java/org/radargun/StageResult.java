package org.radargun;

/**
 * Return value from {@link org.radargun.MasterStage} or {@link org.radargun.DistStage}, that should signal
 * which stage should follow.
 */
public enum StageResult {
   /**
    * Continue with next stage (this stage was successful).
    */
   SUCCESS,
   /**
    * Fail current scenario (this stage was not successful).
    */
   FAIL,
   /**
    * Fail current scenario and do not execute scenarios with any further configurations.
    */
   EXIT,

   /**
    * Break the innermost repeat
    */
   BREAK,

   /**
    * Continue with the next cycle of the innermost repeat
    */
   CONTINUE;

   public boolean isError() {
      return this == FAIL || this == EXIT;
   }
}

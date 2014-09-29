package org.radargun;

/**
 * Return value from {@link org.radargun.MasterStage} or {@link org.radargun.DistStage}, that should signal
 * which stage should follow.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public final class StageResult {
   /**
    * Continue with next stage (this stage was successful).
    */
   public static final StageResult SUCCESS = new StageResult();
   /**
    * Fail current scenario (this stage was not successful).
    */
   public static final StageResult FAIL = new StageResult();
   /**
    * Fail current scenario and do not execute scenarios with any further configurations.
    */
   public static final StageResult EXIT = new StageResult();

   /**
    * Break the innermost repeat
    */
   public static final StageResult BREAK = new StageResult();

   /**
    * Continue with the next cycle of the innermost repeat
    */
   public static final StageResult CONTINUE = new StageResult();

   private StageResult() {
   }

   public boolean isError() {
      return this == FAIL || this == EXIT;
   }
}

package org.radargun.stages.control;

import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.InternalDistStage;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(internal = true, doc = "Parent class for repeat-related stages.")
public abstract class RepeatStage extends InternalDistStage {
   public final static String REPEAT_NAMES = "REPEAT_NAMES";

   @Property(doc = "Repeat name. Default is none.")
   protected String name;

   @Property(doc = "Initial counter value. Default is 0.")
   protected int from = 0;

   @Property(doc = "Maximum counter value. Default is none.")
   protected Integer to;

   @Property(doc = "Counter increment. Default is 1.")
   protected int inc = 1;

   @Property(doc = "Sets from=0, to=times-1. Default is none.")
   private Integer times;

   @Init
   public void init() {
      if (to == null && times == null) {
         throw new IllegalStateException("Must define either 'to' or 'times'");
      } else if (to != null && times != null) {
         throw new IllegalStateException("Define just one of 'to', 'times'");
      } else if (times != null) {
         to = times - 1;
      }
   }

   protected String getCounterName() {
      return "repeat." + (name != null ? name + ".counter" : "counter");
   }
}

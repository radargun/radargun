package org.radargun.stages.monitor;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.logging.Level;
import org.radargun.logging.LogFactory;
import org.radargun.stages.AbstractDistStage;

/**
 * This stage is meant for debugging. Changes log priorities. Beware that some code can cache the is{LogLevel}Enabled().
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Debugging stage: changes log priorities")
public class SetLogLevelStage extends AbstractDistStage {

   @Property(optional = false, name = "package", doc = "The package or class which should be affected.")
   private String pkg;

   @Property(doc = "The new priority that should be used. No defaults.")
   private String priority;

   @Property(doc = "If set to true, instead of setting the priority directly just undo the last priority change. Default is false.")
   private boolean pop;

   private static Map<String, Stack<Level>> stacks = new HashMap<String, Stack<Level>>();

   @Override
   public DistStageAck executeOnSlave() {
      try {
         Stack<Level> stack = stacks.get(pkg);
         if (stack == null) {
            stack = new Stack<Level>();
            stacks.put(pkg, stack);
         }
         if (priority != null) {
            stack.push(LogFactory.getLog(pkg).getLevel());
            LogFactory.getLog(pkg).setLevel(Level.toLevel(priority));
         } else if (pop) {
            if (stack.empty()) {
               log.error("Cannot POP priority level, stack empty!");
            } else {
               LogFactory.getLog(pkg).setLevel(stack.pop());
            }
         } else {
            log.error("Neither priority nor POP request specified");
         }
      } catch (Exception e) {
         log.error("Failed to change log level", e);
      }
      return successfulResponse();
   }
}

package org.radargun.stages;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.utils.ArgsConverter;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Stage that allows you to execute generic command on the slave machine.")
public class CommandStage extends AbstractDistStage {
   @Property(doc = "Command that should be executed.", optional = false)
   private String cmd;

   @Property(doc = "Arguments to this command. Default are none", converter = ArgsConverter.class)
   private List<String> args;

   @Property(doc = "List of exit values that are allowed from the command. Default is {0}.")
   private List<Integer> exitValues = Collections.singletonList(0);

   @Override
   public DistStageAck executeOnSlave() {
      List<String> command = new ArrayList<String>();
      command.add(cmd);
      if (args != null) {
         command.addAll(args);
      }
      log.info("Running: " + command);
      ProcessBuilder pb = new ProcessBuilder().command(command);
      pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
      pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
      pb.redirectError(ProcessBuilder.Redirect.INHERIT);
      try {
         Process process = pb.start();
         int exitValue = process.waitFor();
         if (exitValues.contains(exitValue)) return successfulResponse();
         else return errorResponse("Command finished with exit value " + exitValue);
      } catch (IOException e) {
         return errorResponse("Command failed", e);
      } catch (InterruptedException e) {
         return errorResponse("Interrupted while waiting for the command to finish", e);
      }
   }
}

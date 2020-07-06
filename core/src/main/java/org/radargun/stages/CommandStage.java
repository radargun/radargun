package org.radargun.stages;

import java.io.File;
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
@Stage(doc = "Stage that allows you to execute generic command on the worker machine.")
public class CommandStage extends AbstractDistStage {
   @Property(doc = "Command that should be executed. No default, but must be provided unless 'var' is set.")
   private String cmd;

   @Property(doc = "Arguments to this command. Default are none", converter = ArgsConverter.class)
   private List<String> args;

   @Property(doc = "Argument which won't be parsed. Useful to run piped commands, like sh -c \"echo yes | some interactive script\"")
   private String nonParsedArgs;

   @Property(doc = "List of exit values that are allowed from the command. Default is {0}.")
   private List<Integer> exitValues = Collections.singletonList(0);

   @Property(doc = "Wait until the command finishes. Default is true.")
   private boolean waitForExit = true;

   @Property(doc = "Store/load process into/from state variable. Use this in combination with 'waitForExit=false'. By default the process is not stored/loaded.")
   private String var;

   @Property(doc = "Cancel running command, loaded using the 'var' property. By default not cancelling anything.")
   private boolean cancel = false;

   @Property(doc = "Output file. By default uses standard output.")
   private String out;

   @Property(doc = "Append output to the file instead of overwriting. Default is to overwrite.")
   private boolean outAppend = false;

   @Property(doc = "Error output file. By default uses standard error.")
   private String err;

   @Property(doc = "Append error output to the file instead of overwriting. Default is to overwrite.")
   private boolean errAppend = false;


   @Override
   public DistStageAck executeOnWorker() {
      try {
         Process process;
         if (cmd != null) {
            process = startProcess();
            if (var != null) {
               workerState.put(var, process);
            }
         } else if (var != null) {
            process = (Process) workerState.get(var);
            if (process == null) {
               return errorResponse("Could not find any process identified with '" + var + "'");
            }
         } else {
            return errorResponse("Must specify either 'cmd' or 'var'");
         }
         if (cancel) {
            process.destroy();
         }
         if (waitForExit) {
            int exitValue = process.waitFor();
            if (var != null) {
               workerState.remove(var);
            }
            if (exitValues.contains(exitValue)) return successfulResponse();
            else return errorResponse("Command finished with exit value " + exitValue);
         } else {
            return successfulResponse();
         }
      } catch (IOException e) {
         return errorResponse("Command failed", e);
      } catch (InterruptedException e) {
         return errorResponse("Interrupted while waiting for the command to finish", e);
      }
   }

   protected Process startProcess() throws IOException {
      List<String> command = new ArrayList<String>();
      command.add(cmd);
      if (args != null) {
         command.addAll(args);
      }
      if (nonParsedArgs != null) {
         command.add(nonParsedArgs);
      }
      log.info("Running: " + command);
      ProcessBuilder pb = new ProcessBuilder().command(command);
      pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
      pb.redirectOutput(getDestination(out, outAppend));
      pb.redirectError(getDestination(err, errAppend));
      return pb.start();
   }

   private ProcessBuilder.Redirect getDestination(String file, boolean append) {
      return file == null ? ProcessBuilder.Redirect.INHERIT :
            append ? ProcessBuilder.Redirect.appendTo(new File(file))
                   : ProcessBuilder.Redirect.to(new File(file));
   }
}

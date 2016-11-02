package org.radargun.service;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.radargun.Directories;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.ArgsConverter;
import org.radargun.utils.EnvsConverter;
import org.radargun.utils.TimeConverter;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Service(doc = "Generic process control")
public class ProcessService {

   @Property(doc = "Command for starting the process")
   protected String command;

   @Property(doc = "Configuration file used as the last argument.")
   protected String file;

   @Property(doc = "Additional arguments. Empty by default.", converter = ArgsConverter.class)
   protected List<String> args = Collections.emptyList();

   @Property(doc = "Environment arguments. Empty by default.", converter = EnvsConverter.class)
   protected Map<String,String> env = Collections.emptyMap();

   @Property(doc = "Current operating system. Default is UNIX.")
   protected String os = "unix";

   @Property(doc = "Timeout to start the service. Default is 1 minute.", converter = TimeConverter.class)
   public long startTimeout = 60000;

   @Property(doc = "Timeout to stop the service. Default is 1 minute.", converter = TimeConverter.class)
   public long stopTimeout = 60000;

   @Property(doc = "Process standard error as standard output. Default is false.")
   public boolean stderrToStdout = false;

   private CopyOnWriteArrayList<Action> actions = new CopyOnWriteArrayList<Action>();
   protected String pid;

   @ProvidesTrait
   public ProcessLifecycle createLifecycle() {
      return new ProcessLifecycle(this);
   }

   protected List<String> getCommand() {
      ArrayList<String> command = new ArrayList<String>(args.size() + 2);
      command.add(this.command);
      command.addAll(args);
      if (file != null) {
         command.add(file);
      }
      return command;
   }

   public String getCommandPrefix() {
      return Directories.PLUGINS_DIR + File.separator + "process" + File.separator + "bin" + File.separator + os + "-";
   }

   public String getCommandSuffix() {
      if (!"unix".equals(os)) throw new IllegalArgumentException("Only unix is supported as OS.");
      return ".sh";
   }

   public Map<String, String> getEnvironment() {
      return env;
   }

   public String getPid() {
      return pid;
   }

   public void setPid(String pid) {
      this.pid = pid;
   }

   public void registerAction(Pattern pattern, OutputListener action) {
      actions.add(new Action(pattern, action));
   }

   public void unregisterAction(Pattern pattern) {
      for (Action a : actions) {
         if (a.pattern.equals(pattern)) actions.remove(a);
      }
   }

   public void reportOutput(String line) {
      System.out.println(line);
      for (Action action : actions) {
         Matcher m = action.pattern.matcher(line);
         if (m.matches()) {
            action.listener.run(m);
         }
      }
   }

   public void reportError(String line) {
      if (stderrToStdout) {
         reportOutput(line);
      } else {
         System.err.println(line);
      }
   }

   public interface OutputListener {
      void run(Matcher matcher);
   }

   private static class Action {
      public final Pattern pattern;
      public final OutputListener listener;

      private Action(Pattern pattern, OutputListener listener) {
         this.pattern = pattern;
         this.listener = listener;
      }
   }
}

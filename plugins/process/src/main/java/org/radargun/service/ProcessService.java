package org.radargun.service;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import org.radargun.Directories;
import org.radargun.Service;
import org.radargun.config.Converter;
import org.radargun.config.Property;
import org.radargun.utils.ArgsConverter;
import org.radargun.utils.TimeConverter;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.ProvidesTrait;

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

   @Property(doc = "Timeout to start the server. Default is 1 minute.", converter = TimeConverter.class)
   public long startTimeout = 60000;

   @Property(doc = "Process standard error as standard output. Default is false.")
   public boolean stderrToStdout = false;

   private CopyOnWriteArrayList<Action> actions = new CopyOnWriteArrayList<Action>();
   protected String tag;

   public ProcessService() {
      Random random = new Random();
      StringBuilder sb = new StringBuilder(16);
      for (int i = 0; i < 16; ++i) {
         sb.append((char) (random.nextInt('z' - 'a' + 1) + 'a'));
      }
      tag = sb.toString();
   }

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

   public String getCommandTag() {
      return tag;
   }

   public Map<String, String> getEnvironment() {
      return env;
   }

   public void registerAction(Pattern pattern, Runnable action) {
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
         if (action.pattern.matcher(line).matches()) {
            action.runnable.run();
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

   private static class EnvsConverter implements Converter<Map<String, String>> {
      private static Log log = LogFactory.getLog(EnvsConverter.class);

      @Override
      public Map<String, String> convert(String string, Type type) {
         Map<String, String> env = new TreeMap<String, String>();
         String[] lines = string.split("\n");
         for (String line : lines) {
            int eqIndex = line.indexOf('=');
            if (eqIndex < 0) {
               if (line.trim().length() > 0) {
                  log.warn("Cannot parse env " + line);
               }
            } else {
               env.put(line.substring(0, eqIndex).trim(), line.substring(eqIndex + 1).trim());
            }
         }
         return env;
      }

      @Override
      public String convertToString(Map<String, String> value) {
         StringBuilder sb = new StringBuilder();
         for (Map.Entry<String, String> envVar : value.entrySet()) {
            sb.append(envVar.getKey()).append('=').append(envVar.getValue()).append('\n');
         }
         return sb.toString();
      }

      @Override
      public String allowedPattern(Type type) {
         return Converter.ANY_MULTI_LINE;
      }
   }

   private static class Action {
      public final Pattern pattern;
      public final Runnable runnable;

      private Action(Pattern pattern, Runnable runnable) {
         this.pattern = pattern;
         this.runnable = runnable;
      }
   }
}

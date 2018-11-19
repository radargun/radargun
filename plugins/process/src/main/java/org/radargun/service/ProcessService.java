package org.radargun.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.radargun.Directories;
import org.radargun.Service;
import org.radargun.ServiceHelper;
import org.radargun.config.FileConverter;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.ArgsConverter;
import org.radargun.utils.EnvsConverter;
import org.radargun.utils.TimeConverter;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Service(doc = "Generic process control")
public class ProcessService {

   protected final Log log = LogFactory.getLog(getClass());

   @Property(doc = "Command for starting the process")
   protected String command;

   @Property(doc = "Configuration file used as the last argument.")
   private String file;

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

   protected ProcessLifecycle<?> lifecycle;
   
   @ProvidesTrait
   public ProcessLifecycle<?> createLifecycle() {
      return lifecycle;
   }

   protected List<String> getCommand() {
      ArrayList<String> command = new ArrayList<String>(args.size() + 2);
      command.add(this.command);
      command.addAll(args);
      if (file != null) {
         command.add(evaluateFile((evaluatedFile) -> {
            return evaluatedFile;
         }));
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

   /**
    * When @getCommand was overwritten the file can be evaluated later
    * @param fn function that receive the evaluated file and return the custom path for the file
    * @return the file evaluate
    */
   public String evaluateFile(Function<String, String> fn) {
      this.file = fn.apply(new FileConverter().convertToString(file));
      return this.file;
   }

   public String evaluateFile(String home, String... confDir) {
      String evaluatedFile = evaluateFile((f) -> f);
      Path filesystemFile;
      try {
         URL resource = getClass().getResource("/" + evaluatedFile);
         filesystemFile = FileSystems.getDefault().getPath(evaluatedFile);
         Path target = FileSystems.getDefault().getPath(home, appendArray(confDir, "radargun-" + ServiceHelper.getContext().getSlaveIndex() + ".xml"));
         if (resource != null) {
            try (InputStream is = resource.openStream()) {
               log.info("Found " + evaluatedFile + " as a resource");
               Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            }
         } else if (filesystemFile.toFile().exists()) {
            log.info("Found " + evaluatedFile + " in plugin directory");
            Files.copy(filesystemFile, target, StandardCopyOption.REPLACE_EXISTING);
         } else if (FileSystems.getDefault().getPath(home, appendArray(confDir, evaluatedFile)).toFile().exists()) {
            log.info("Found " + evaluatedFile + " in " + Arrays.toString(confDir) + " directory");
            filesystemFile = FileSystems.getDefault().getPath(home, appendArray(confDir, evaluatedFile));
            Files.copy(filesystemFile, target, StandardCopyOption.REPLACE_EXISTING);
         } else {
            throw new FileNotFoundException("File " + evaluatedFile + " not found neither as resource nor in filesystem.");
         }
      } catch (IOException e) {
         //log.error("Failed to copy file", e);
         throw new RuntimeException(e);
      }
      return filesystemFile.toString();
   }

   private static <T> T[] appendArray(T[] elements, T element) {
      T[] newArray = Arrays.copyOf(elements, elements.length + 1);
      newArray[elements.length] = element;
      return newArray;
   }

   public String getFile() {
      return this.file;
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

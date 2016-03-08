package org.radargun.config;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.utils.ReflexiveConverters;
import org.radargun.utils.SizeConverter;

/**
 * Holds VM arguments configuration.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class VmArgs implements Serializable {
   private static final Log log = LogFactory.getLog(VmArgs.class);

   @Property(doc = "Ignore all VM arguments passed to slave and use only those specified here. Default is false.")
   private boolean ignoreDefault = false;

   @PropertyDelegate(prefix = "memory.")
   private Memory memory = new Memory();

   @PropertyDelegate(prefix = "gc.")
   private Gc gc = new Gc();

   @PropertyDelegate(prefix = "flight-recorder.")
   private FlightRecorder flightRecorder = new FlightRecorder();

   // TODO: add more properties

   @Property(doc = "Properties (-Dfoo=bar)", complexConverter = Prop.Converter.class)
   private List<Prop> properties = Collections.EMPTY_LIST;

   @Property(doc = "Custom arguments.", complexConverter = Custom.Converter.class)
   private List<Custom> customArguments = Collections.EMPTY_LIST;

   public List<String> getVmArgs(Collection<String> defaultVmArgs) {
      List<String> vmArgs = ignoreDefault ? new LinkedList<String>() : new LinkedList<>(defaultVmArgs);
      for (Path path : PropertyHelper.getProperties(this.getClass(), true, true, false).values()) {
         try {
            Object value = path.get(this);
            if (value instanceof VmArg) {
               ((VmArg) value).setArgs(vmArgs);
            }
         } catch (IllegalAccessException e) {
            log.warn("Failed to read path " + path, e);
         }
      }
      for (Custom c : customArguments) {
         c.setArgs(vmArgs);
      }
      for (Prop p : properties) {
         replace(vmArgs, "-D" + p.name + "=", p.value);
      }
      return vmArgs;
   }

   private static void replace(Collection<String> args, String prefix, String value) {
      for (Iterator<String> it = args.iterator(); it.hasNext(); ) {
         String arg = it.next();
         if (arg.startsWith(prefix)) {
            it.remove();
         }
      }
      args.add(prefix + value);
   }

   private static void set(Collection<String> args, String option, boolean on) {
      Pattern pattern = Pattern.compile("-XX:." + option);
      for (Iterator<String> it = args.iterator(); it.hasNext(); ) {
         String arg = it.next();
         if (pattern.matcher(arg).matches()) {
            it.remove();
         }
      }
      args.add("-XX:" + (on ? '+' : '-') + option);
   }

   private static void ensureArg(Collection<String> args, String arg) {
      if (!args.contains(arg)) args.add(arg);
   }

   public interface VmArg extends Serializable {
      /* Override arguments */
      void setArgs(Collection<String> args);
   }

   private static class Memory implements VmArg {
      @Property(doc = "Max memory", converter = SizeConverter.class)
      private Long max;

      @Property(doc = "Min memory", converter = SizeConverter.class)
      private Long min;

      @Override
      public void setArgs(Collection<String> args) {
         if (min != null) replace(args, "-Xms", String.valueOf(min));
         if (max != null) replace(args, "-Xmx", String.valueOf(max));
      }
   }

   private static class FlightRecorder implements VmArg {
      @Property(doc = "Start flight recording for the benchmark.", optional = false)
      private boolean enabled = false;

      @Property(doc = "File for the recording.")
      private String filename;

      @Property(doc = "Settings file with recording configuration.")
      private String settings;

      @Override
      public void setArgs(Collection<String> args) {
         if (!enabled) return;
         StringBuilder recordingParams = new StringBuilder("=compress=false,delay=10s,duration=24h");
         if (filename != null) recordingParams.append(",filename=").append(filename);
         if (settings != null) recordingParams.append(",settings=").append(settings);
         ensureArg(args, "-XX:+UnlockCommercialFeatures");
         ensureArg(args, "-XX:+FlightRecorder");
         replace(args, "-XX:StartFlightRecording", recordingParams.toString());
      }
   }

   @DefinitionElement(name = "custom", doc = "Custom argument to VM.")
   private static class Custom implements VmArg {
      @Property(doc = "Argument as pasted on command-line")
      private String arg;

      @Override
      public void setArgs(Collection<String> args) {
         // TODO: override instead of add
         args.add(arg);
      }

      private static class Converter extends ReflexiveConverters.ListConverter {
         public Converter() {
            super(new Class[] {Custom.class});
         }
      }
   }

   private static class Gc implements VmArg {
      @Property(doc = "Verbose GC log.")
      Boolean printGc;

      @Property(doc = "Print more information about the GC.")
      Boolean printGcDetails;

      @Property(doc = "Print timestamps of the GC.")
      Boolean printGcTimestamps;

      @Property(doc = "Log file")
      String logFile;

      @Override
      public void setArgs(Collection<String> args) {
         if (printGc != null) set(args, "PrintGC", printGc);
         if (printGcDetails != null) set(args, "PrintGCDetails", printGcDetails);
         if (printGcTimestamps != null) set(args, "PrintGCTimeStamps", printGcTimestamps);
         if (logFile != null) replace(args, "-Xloggc:", logFile);
      }
   }

   @DefinitionElement(name = "property", doc = "JVM property, usually set by -D")
   private static class Prop implements Serializable {
      @Property(doc = "Name of the property", optional = false)
      private String name;

      @Property(doc = "Value of the property", optional = false)
      private String value;

      public static class Converter extends ReflexiveConverters.ListConverter {
         public Converter() {
            super(new Class[] {Prop.class});
         }
      }
   }
}

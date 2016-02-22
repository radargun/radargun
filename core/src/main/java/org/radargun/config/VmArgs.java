package org.radargun.config;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
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

   @PropertyDelegate(prefix = "jit.")
   private Jit jit = new Jit();

   @PropertyDelegate(prefix = "flight-recorder.")
   private FlightRecorder flightRecorder = new FlightRecorder();

   @PropertyDelegate(prefix = "class-loading.")
   private ClassLoading classLoading = new ClassLoading();

   @Property(name="unlock-diagnostic-vm-options", doc = "Unlock diagnostic VM options. Some other VM options may trigger this automically.")
   private Boolean unlockDiagnosticVMOptions;

   // TODO: add more properties

   @Property(doc = "Properties (-Dfoo=bar)", complexConverter = Prop.Converter.class)
   private List<Prop> properties = Collections.EMPTY_LIST;

   @Property(doc = "Custom arguments.", complexConverter = Custom.Converter.class)
   private List<Custom> customArguments = Collections.EMPTY_LIST;

   public List<String> getVmArgs(Collection<String> defaultVmArgs) {
      List<String> vmArgs = ignoreDefault ? new LinkedList<String>() : new LinkedList<>(defaultVmArgs);
      Collection<Path> properties = PropertyHelper.getProperties(this.getClass(), true, true, false).values();
      if (unlockDiagnosticVMOptions == null) {
         for (Path path : properties) {
            if (path.isAnnotationPresent(RequireDiagnostic.class)) {
               Object value = null;
               try {
                  value = path.get(this);
               } catch (IllegalAccessException e) {
                  throw new IllegalStateException(e);
               }
               if (value != null) {
                  if (Boolean.FALSE.equals(unlockDiagnosticVMOptions)) {
                     throw new IllegalStateException("JVM flag requires diagnostic VM options");
                  } else {
                     unlockDiagnosticVMOptions = Boolean.TRUE;
                  }
               }
            }
         }
      }
      if (unlockDiagnosticVMOptions != null) {
         set(vmArgs, "UnlockDiagnosticVMOptions", unlockDiagnosticVMOptions);
      }
      for (Path path : properties) {
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
      for (Prop p : this.properties) {
         replace(vmArgs, "-D" + p.name + "=", p.value);
      }
      return vmArgs;
   }

   private static void replace(List<String> args, String prefix, String value) {
      for (Iterator<String> it = args.iterator(); it.hasNext(); ) {
         String arg = it.next();
         if (arg.startsWith(prefix)) {
            it.remove();
         }
      }
      args.add(prefix + value);
   }

   private static void set(List<String> args, String option, boolean on) {
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

   @Retention(RetentionPolicy.RUNTIME)
   @Target(ElementType.FIELD)
   private @interface RequireDiagnostic {}

   public interface VmArg extends Serializable {
      /* Override arguments */
      void setArgs(List<String> args);
   }

   private class Memory implements VmArg {
      @Property(doc = "Max memory", converter = SizeConverter.class)
      private Long max;

      @Property(doc = "Min memory", converter = SizeConverter.class)
      private Long min;

      @Override
      public void setArgs(List<String> args) {
         if (min != null) replace(args, "-Xms", String.valueOf(min));
         if (max != null) replace(args, "-Xmx", String.valueOf(max));
      }
   }

   private class FlightRecorder implements VmArg {
      @Property(doc = "Start flight recording for the benchmark.", optional = false)
      private boolean enabled = false;

      @Property(doc = "File for the recording.")
      private String filename;

      @Property(doc = "Settings file with recording configuration.")
      private String settings;

      @Override
      public void setArgs(List<String> args) {
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
      public void setArgs(List<String> args) {
         // TODO: override instead of add
         args.add(arg);
      }

      private static class Converter extends ReflexiveConverters.ListConverter {
         public Converter() {
            super(new Class[] {Custom.class});
         }
      }
   }

   private class Gc implements VmArg {
      @Property(doc = "Verbose GC log.")
      Boolean printGc;

      @Property(doc = "Print more information about the GC.")
      Boolean printGcDetails;

      @Property(doc = "Print timestamps of the GC.")
      Boolean printGcTimestamps;

      @Property(doc = "Log file")
      String logFile;

      @Override
      public void setArgs(List<String> args) {
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

   private class Jit implements VmArg {
      @Property(doc = "Preserve frame pointers in compiled code.")
      Boolean preserveFramePointer;

      @Property(doc = "Print compilation.")
      Boolean printCompilation;

      @RequireDiagnostic
      @Property(doc = "Print generated assembly code.")
      Boolean printAssembly;

      @RequireDiagnostic
      @Property(doc = "Print method inlining")
      Boolean printInlining;

      @RequireDiagnostic
      @Property(doc = "Log compilation.")
      Boolean logCompilation;

      @RequireDiagnostic
      @Property(doc = "Log file for log-compilation")
      String logFile;

      @Property(doc = "Maximum size of method bytecode to consider for inlining.")
      Integer freqInlineSize;

      @Property(doc = "Maximum size of method bytecode for automatic inlining.")
      Integer maxInlineSize;

      @Property(doc = "Maximum number of method calls that can be inlined.")
      Integer maxInlineLevel;

      @Property(doc = "Inline a previously compiled method only if its generated native code size is less than this")
      Integer inlineSmallCode;

      @Override
      public void setArgs(List<String> args) {
         if (preserveFramePointer != null) set(args, "PreserveFramePointer", preserveFramePointer);
         if (printCompilation != null) set(args, "PrintCompilation", printCompilation);
         if (printAssembly != null) set(args, "PrintAssembly", printAssembly);
         if (printInlining != null) set(args, "PrintInlining", printInlining);
         if (logCompilation != null) set(args, "LogCompilation", logCompilation);
         if (logFile != null) replace(args, "-XX:LogFile=", logFile);
         if (freqInlineSize != null) replace(args, "-XX:FreqInlineSize=", freqInlineSize.toString());
         if (maxInlineSize != null) replace(args, "-XX:MaxInlineSize=", maxInlineSize.toString());
         if (maxInlineLevel != null) replace(args, "-XX:MaxInlineLevel=", maxInlineLevel.toString());
         if (inlineSmallCode != null) replace(args, "-XX:InlineSmallCode=", inlineSmallCode.toString());
      }
   }

   private class ClassLoading implements VmArg {
      @RequireDiagnostic
      @Property(doc = "Trace class loading")
      Boolean traceClassLoading;

      @Override
      public void setArgs(List<String> args) {
         if (traceClassLoading != null) set(args, "TraceClassLoading", traceClassLoading);
      }
   }
}

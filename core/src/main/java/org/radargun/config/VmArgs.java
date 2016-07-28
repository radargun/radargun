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
import org.radargun.utils.NumberConverter;
import org.radargun.utils.ReflexiveConverters;
import org.radargun.utils.SizeConverter;

/**
 * Holds VM arguments configuration. Options descriptions are here:
 * http://docs.oracle.com/javase/8/docs/technotes/tools/unix/java.html All options are here:
 * http://hg.openjdk.java.net/jdk8/jdk8/hotspot/file/tip/src/share/vm/runtime/globals.hpp Another
 * good resource:
 * http://stas-blogspot.blogspot.com/2011/07/most-complete-list-of-xx-options-for.html
 */
public class VmArgs implements Serializable {
   private static final Log log = LogFactory.getLog(VmArgs.class);

   @Property(doc = "Ignore all VM arguments passed to slave and use only those specified here. Default is false.")
   private Boolean ignoreDefault = false;

   @Property(doc = "Print all VM arguments. Default is false.")
   private Boolean printFlagsFinal = false;

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

   @Property(name = "unlock-diagnostic-vm-options", doc = "Unlock diagnostic VM options. Some other VM options may trigger this automically.")
   private Boolean unlockDiagnosticVMOptions;

   @Property(name = "unlock-experimental-vm-options", doc = "Unlock Experimental VM options.")
   private Boolean unlockExperimentalVMOptions = false;

   @Property(doc = "Properties (-Dfoo=bar)", complexConverter = Prop.Converter.class)
   private List<Prop> properties = Collections.emptyList();

   @Property(doc = "Custom arguments.", complexConverter = Custom.Converter.class)
   private List<Custom> customArguments = Collections.emptyList();

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
      if (unlockExperimentalVMOptions) {
         set(vmArgs, "UnlockExperimentalVMOptions", unlockExperimentalVMOptions);
      }
      if (printFlagsFinal) {
         set(vmArgs, "PrintFlagsFinal", printFlagsFinal);
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
      for (Iterator<String> it = args.iterator(); it.hasNext();) {
         String arg = it.next();
         if (arg.startsWith(prefix)) {
            it.remove();
         }
      }
      args.add(prefix + value);
   }

   private static void set(List<String> args, String option, boolean on) {
      Pattern pattern = Pattern.compile("-XX:." + option);
      for (Iterator<String> it = args.iterator(); it.hasNext();) {
         String arg = it.next();
         if (pattern.matcher(arg).matches()) {
            it.remove();
         }
      }
      args.add("-XX:" + (on ? '+' : '-') + option);
   }

   private static void ensureArg(Collection<String> args, String arg) {
      if (!args.contains(arg))
         args.add(arg);
   }

   @Retention(RetentionPolicy.RUNTIME)
   @Target(ElementType.FIELD)
   private @interface RequireDiagnostic {
   }

   public interface VmArg extends Serializable {
      /* Override arguments */
      void setArgs(List<String> args);
   }

   private class Memory implements VmArg {
      @Property(doc = "Max memory", converter = SizeConverter.class)
      private Long max;

      @Property(doc = "Min memory", converter = SizeConverter.class)
      private Long min;

      @Property(doc = "Initial size of the young generation", converter = SizeConverter.class)
      private Long newSize;

      @Property(doc = "Maximum size of the young generation", converter = SizeConverter.class)
      private Long maxNewSize;

      @Property(doc = "Thread stack size", converter = SizeConverter.class)
      private Long threadStackSize;

      @Property(doc = "Sets the ratio between young and old generation sizes", converter = NumberConverter.class)
      private Integer newRatio;

      @Property(doc = "Enables the use of large page memory.")
      private Boolean useLargePages;

      @Override
      public void setArgs(List<String> args) {
         if (min != null)
            replace(args, "-Xms", String.valueOf(min));
         if (max != null)
            replace(args, "-Xmx", String.valueOf(max));
         if (newSize != null)
            replace(args, "-XX:NewSize=", String.valueOf(newSize));
         if (maxNewSize != null)
            replace(args, "-XX:MaxNewSize=", String.valueOf(maxNewSize));
         if (threadStackSize != null)
            replace(args, "-Xss", String.valueOf(threadStackSize));
         if (newRatio != null)
            replace(args, "-XX:NewRatio=", String.valueOf(newRatio));
         if (useLargePages != null)
            set(args, "UseLargePages", useLargePages);
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
         if (!enabled)
            return;
         StringBuilder recordingParams = new StringBuilder("=compress=false,delay=10s,duration=24h");
         if (filename != null)
            recordingParams.append(",filename=").append(filename);
         if (settings != null)
            recordingParams.append(",settings=").append(settings);
         ensureArg(args, "-XX:+UnlockCommercialFeatures");
         ensureArg(args, "-XX:+FlightRecorder");
         replace(args, "-XX:StartFlightRecording", recordingParams.toString());
      }
   }

   @DefinitionElement(name = "custom", doc = "Custom argument to VM.")
   private static class Custom implements VmArg {
      @Property(doc = "Argument as pasted on command-line", optional = false)
      private String arg;

      @Override
      public void setArgs(List<String> args) {
         // TODO: override instead of add
         args.add(arg);
      }

      private static class Converter extends ReflexiveConverters.ListConverter {
         public Converter() {
            super(new Class[] { Custom.class });
         }
      }
   }

   private class Gc implements VmArg {
      @Property(doc = "Verbose GC log.")
      private Boolean printGc;

      @Property(doc = "Print more information about the GC.")
      private Boolean printGcDetails;

      @Property(doc = "Print timestamps of the GC.")
      private Boolean printGcTimestamps;

      @Property(doc = "Log file")
      private String logFile;

      @Property(doc = "Enables the use of the parallel scavenge garbage collector (also known "
            + "as the throughput collector) to improve the performance of your application by "
            + "leveraging multiple processors.")
      private Boolean useParallelGC;

      @Property(doc = "Sets the number of threads used for parallel garbage collection in the "
            + "young and old generations.", converter = NumberConverter.class)
      private Integer parallelGCThreads;

      @Property(doc = "Enables the use of the parallel garbage collector for full GCs.")
      private Boolean useParallelOldGC;

      @Property(doc = "Enables the use of the CMS garbage collector for the old generation.")
      private Boolean useConcMarkSweepGC;

      @Property(doc = "Enables the use of the garbage-first (G1) garbage collector.")
      private Boolean useG1GC;

      @Property(doc = "Enables the option that disables processing of calls to System.gc().")
      private Boolean disableExplicitGC;

      @Property(doc = "Sets the percentage of the heap occupancy (0 to 100) at which to start "
            + "a concurrent GC cycle.", converter = NumberConverter.class)
      private Integer initiatingHeapOccupancyPercent;

      @Property(doc = "Sets a target for the maximum GC pause time (in milliseconds).", converter = NumberConverter.class)
      private Integer maxGCPauseMillis;

      @Property(doc = "Sets the time interval over which GC pauses totaling up to "
            + "MaxGCPauseMillis may take place:", converter = NumberConverter.class)
      private Integer gcPauseIntervalMillis;

      @Property(doc = "Enables Java heap optimization.")
      private Boolean aggressiveHeap;

      @Property(doc = "Enables the use of aggressive performance optimization features, "
            + "which are expected to become default in upcoming releases.")
      private Boolean aggressiveOpts;

      @Property(doc = "Enables scavenging attempts before the CMS remark step.")
      private Boolean cmsScavengeBeforeRemark;

      @Property(doc = "Sets the percentage (0 to 100) of the value specified by "
            + "-XX:MinHeapFreeRatio that is allocated before a CMS collection cycle "
            + "commences.", converter = NumberConverter.class)
      private Integer cmsTriggerRatio;

      @Property(doc = "Adaptive size policy application time to GC time ratio", converter = NumberConverter.class)
      private Integer gcTimeRatio;

      @Override
      public void setArgs(List<String> args) {
         if (printGc != null)
            set(args, "PrintGC", printGc);
         if (printGcDetails != null)
            set(args, "PrintGCDetails", printGcDetails);
         if (printGcTimestamps != null)
            set(args, "PrintGCTimeStamps", printGcTimestamps);
         if (logFile != null)
            replace(args, "-Xloggc:", logFile);
         if (useParallelGC != null)
            set(args, "UseParallelGC", useParallelGC);
         if (useParallelOldGC != null)
            set(args, "UseParallelOldGC", useParallelOldGC);
         if (parallelGCThreads != null)
            replace(args, "-XX:ParallelGCThreads=", String.valueOf(parallelGCThreads));
         if (useConcMarkSweepGC != null)
            set(args, "UseConcMarkSweepGC", useConcMarkSweepGC);
         if (useG1GC != null)
            set(args, "UseG1GC", useG1GC);
         if (disableExplicitGC != null)
            set(args, "DisableExplicitGC", disableExplicitGC);
         if (initiatingHeapOccupancyPercent != null)
            replace(args, "-XX:InitiatingHeapOccupancyPercent=", String.valueOf(initiatingHeapOccupancyPercent));
         if (maxGCPauseMillis != null)
            replace(args, "-XX:MaxGCPauseMillis=", String.valueOf(maxGCPauseMillis));
         if (gcPauseIntervalMillis != null)
            replace(args, "-XX:GCPauseIntervalMillis=", String.valueOf(gcPauseIntervalMillis));
         if (aggressiveHeap != null)
            set(args, "AggressiveHeap", aggressiveHeap);
         if (aggressiveOpts != null)
            set(args, "AggressiveOpts", aggressiveOpts);
         if (cmsScavengeBeforeRemark != null)
            set(args, "CMSScavengeBeforeRemark", cmsScavengeBeforeRemark);
         if (cmsTriggerRatio != null)
            replace(args, "-XX:CMSTriggerRatio=", String.valueOf(cmsTriggerRatio));
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
            super(new Class[] { Prop.class });
         }
      }
   }

   private class Jit implements VmArg {
      @Property(doc = "Preserve frame pointers in compiled code.")
      private Boolean preserveFramePointer;

      @Property(doc = "Print compilation.")
      private Boolean printCompilation;

      @RequireDiagnostic
      @Property(doc = "Print generated assembly code.")
      private Boolean printAssembly;

      @RequireDiagnostic
      @Property(doc = "Print method inlining")
      private Boolean printInlining;

      @RequireDiagnostic
      @Property(doc = "Log compilation.")
      private Boolean logCompilation;

      @RequireDiagnostic
      @Property(doc = "Log file for log-compilation")
      private String logFile;

      @Property(doc = "Maximum size of method bytecode to consider for inlining.")
      private Integer freqInlineSize;

      @Property(doc = "Maximum size of method bytecode for automatic inlining.")
      private Integer maxInlineSize;

      @Property(doc = "Maximum number of method calls that can be inlined.")
      private Integer maxInlineLevel;

      @Property(doc = "Inline a previously compiled method only if its generated native code size is less than this")
      private Integer inlineSmallCode;

      @Property(doc = "Controls the use of tiered compilation")
      private Boolean tieredCompilation;

      @Override
      public void setArgs(List<String> args) {
         if (preserveFramePointer != null)
            set(args, "PreserveFramePointer", preserveFramePointer);
         if (printCompilation != null)
            set(args, "PrintCompilation", printCompilation);
         if (printAssembly != null)
            set(args, "PrintAssembly", printAssembly);
         if (printInlining != null)
            set(args, "PrintInlining", printInlining);
         if (logCompilation != null)
            set(args, "LogCompilation", logCompilation);
         if (logFile != null)
            replace(args, "-XX:LogFile=", logFile);
         if (freqInlineSize != null)
            replace(args, "-XX:FreqInlineSize=", freqInlineSize.toString());
         if (maxInlineSize != null)
            replace(args, "-XX:MaxInlineSize=", maxInlineSize.toString());
         if (maxInlineLevel != null)
            replace(args, "-XX:MaxInlineLevel=", maxInlineLevel.toString());
         if (inlineSmallCode != null)
            replace(args, "-XX:InlineSmallCode=", inlineSmallCode.toString());
         if (tieredCompilation != null)
            set(args, "TieredCompilation", tieredCompilation);
      }
   }

   private class ClassLoading implements VmArg {
      @RequireDiagnostic
      @Property(doc = "Trace class loading")
      private Boolean traceClassLoading;

      @Override
      public void setArgs(List<String> args) {
         if (traceClassLoading != null)
            set(args, "TraceClassLoading", traceClassLoading);
      }
   }
}
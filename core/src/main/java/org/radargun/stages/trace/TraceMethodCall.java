package org.radargun.stages.trace;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;

import org.HdrHistogram.Histogram;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class TraceMethodCall {

   private static Log log = LogFactory.getLog(TraceMethodCall.class);

   @Property(doc = "The highest value to be tracked by the histogram. Must be a positive integer that is >= 2")
   private long highestTrackableValue;

   @Property(doc = "Specifies the precision to use")
   private int numberOfSignificantValueDigits;

   @Property(doc = "Class to trace")
   private String className;

   @Property(doc = "Method to trace. You can trace multiple methods in the same class separating by ;")
   private String methodName;

   @Property(doc = "Method arguments. String of full class name separated by comma. You can trace multiple methods in the same class separating by ;")
   private String arguments;

   @Property(doc = "The scaling factor by which to divide histogram recorded values units in output. Default is 1000")
   private double outputValueUnitScalingRatio = 1000;

   public void start() {
      // the fields are static, the method that intercept must be static
      MethodInterceptor.highestTrackableValue = highestTrackableValue;
      MethodInterceptor.numberOfSignificantValueDigits = numberOfSignificantValueDigits;
      try {
         String[] methodNameData = methodName.split(";");
         String[] argumentsData = arguments.split(";");
         if (methodNameData.length != argumentsData.length) {
            throw new IllegalStateException(String.format("methodName(%d) and arguments(%d) must have the same size", methodNameData.length, argumentsData.length));
         }
         new AgentBuilder.Default()
               .type(ElementMatchers.isSubTypeOf(Class.forName(className)))
               .transform((builder, type, classLoader, module, protectionDomain) -> {
                  Class<MethodInterceptor> interceptor = MethodInterceptor.class;
                  for (int i = 0; i < methodNameData.length; i++) {
                     String innerMethodName = methodNameData[i].trim();
                     String[] innerArguments = argumentsData[i].trim().split(",");
                     Class<?>[] argumentTypes = new Class<?>[innerArguments.length];
                     for (int j = 0; j < argumentTypes.length; j++) {
                        try {
                           argumentTypes[j] = Class.forName(innerArguments[j].trim());
                        } catch (ClassNotFoundException e) {
                           throw new RuntimeException(e);
                        }
                     }
                     builder = builder.method(named(innerMethodName).and(takesArguments(argumentTypes))).intercept(MethodDelegation.to(interceptor));
                  }
                  return builder;
               }).installOn(ByteBuddyAgent.install());
      } catch (ClassNotFoundException e) {
         throw new RuntimeException(e);
      }
   }

   public void dump() {
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      final String utf8 = StandardCharsets.UTF_8.name();
      try (PrintStream ps = new PrintStream(baos, true, utf8)) {
         // easy to compare
         List<String> keys = new ArrayList<>(MethodInterceptor.histogramMap.keySet());
         Collections.sort(keys);
         StringBuilder shortSummary = new StringBuilder("Key,Min,Max,Mean,StdDeviation");
         for (String key : keys) {
            Histogram histogram = MethodInterceptor.histogramMap.get(key);
            // prevent ConcurrentModificationException - ConcurrentHistogram is not working
            synchronized (histogram) {
               ps.format("-----%s-----\n", key);
               histogram.outputPercentileDistribution(ps, outputValueUnitScalingRatio);
               ps.format("-----%s-----\n", key);

               shortSummary.append(String.format("\n%s,%f,%f,%f,%f", key,
                     histogram.getMinValue() / outputValueUnitScalingRatio,
                     histogram.getMaxValue() / outputValueUnitScalingRatio,
                     histogram.getMean() / outputValueUnitScalingRatio,
                     histogram.getStdDeviation() / outputValueUnitScalingRatio));
            }
         }
         String data = baos.toString(utf8);
         log.info("\n" + data + "\n" + shortSummary);
      } catch (UnsupportedEncodingException e) {
         throw new RuntimeException(e);
      }
   }

   public String getClassName() {
      return className;
   }
}

package org.radargun.stages.trace;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;

import org.radargun.config.Property;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class TraceMethodCall {

   @Property(doc = "The highest value to be tracked by the histogram. Must be a positive integer that is >= 2")
   private long highestTrackableValue;

   @Property(doc = "Specifies the precision to use")
   private int numberOfSignificantValueDigits;

   @Property(doc = "Class to trace")
   private String className;

   @Property(doc = "Method to trace")
   private String methodName;

   @Property(doc = "Method arguments. String of full class name separated by comma")
   private String arguments;

   public void start() {
      try {
         String[] argumentsData = arguments.split(",");
         Class<?>[] argumentTypes = new Class<?>[argumentsData.length];
         for (int i = 0; i < argumentTypes.length; i++) {
            argumentTypes[i] = Class.forName(argumentsData[i].trim());
         }
         // the fields are static, the method that intercept must be static
         MethodInterceptor.highestTrackableValue = highestTrackableValue;
         MethodInterceptor.numberOfSignificantValueDigits = numberOfSignificantValueDigits;
         AgentBuilder.Default agentBuilder = new AgentBuilder.Default();
         agentBuilder
               .type(ElementMatchers.isSubTypeOf(Class.forName(className)))
               .transform((builder, type, classLoader, module, protectionDomain) -> {
                        return builder.method(named(methodName).and(takesArguments(argumentTypes))).intercept(MethodDelegation.to(MethodInterceptor.class));
                     }
               )
               .installOn(ByteBuddyAgent.install());
      } catch (ClassNotFoundException e) {
         throw new RuntimeException(e);
      }
   }

   public void dump() {
      MethodInterceptor.histogramMap.forEach((k, v) -> {
         System.out.println("-----" + k + "-----");
         v.outputPercentileDistribution(System.out, 1000.0);
      });
   }

   public String getClassName() {
      return className;
   }
}

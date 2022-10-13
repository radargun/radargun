package org.radargun.stages.trace;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import org.HdrHistogram.Histogram;

public class MethodInterceptor {

   static long highestTrackableValue;
   static int numberOfSignificantValueDigits;
   static Map<String, Histogram> histogramMap = new HashMap<>();

   private MethodInterceptor() {

   }

   @RuntimeType
   public static Object intercept(@Origin Class clazz, @AllArguments Object[] args, @SuperCall Callable<?> callable, @Origin Method method) throws Exception {
      String key = method.getName() + Arrays.toString(method.getParameterTypes()) + "::" + clazz.getSimpleName();
      Histogram histogram;
      synchronized (key) {
         histogram = histogramMap.get(key);
         if (histogram == null) {
            histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
            histogramMap.put(key, histogram);
         }
      }
      long start = System.currentTimeMillis();
      Object value = callable.call();
      long end = System.currentTimeMillis();
      // prevent ConcurrentModificationException - ConcurrentHistogram is not working
      synchronized (histogram) {
         histogram.recordValue(end - start);
      }

      return value;
   }
}

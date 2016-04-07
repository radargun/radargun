package org.radargun.stats.representation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract generic representation that contains time-framed series of representations.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class AbstractSeries<T> {
   private static final Map<Class<? extends AbstractSeries>, Class<?>> representations = new ConcurrentHashMap<>();

   public final long startTime;
   public final long period;
   public final T[] samples;

   protected AbstractSeries(long startTime, long period, T[] samples) {
      this.startTime = startTime;
      this.period = period;
      this.samples = samples;
   }

   static <U> void register(Class<? extends AbstractSeries<U>> seriesClass, Class<? extends U> representationClass) {
      representations.put(seriesClass, representationClass);
   }

   public static <U> Class<U> representation(Class<? extends AbstractSeries<U>> seriesClass) {
      return (Class<U>) representations.get(seriesClass);
   }
}

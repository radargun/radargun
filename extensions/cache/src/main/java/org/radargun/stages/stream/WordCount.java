package org.radargun.stages.stream;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Roman Macor &lt;rmacor@redhat.com&gt;
 */
public class WordCount implements StreamStage.StreamFunction {

   private Map<Object, Object> result;

   @Override
   public Object apply(Stream stream) {
      Object result = stream.map((Serializable & Function<Map.Entry<String, String>, String[]>) e -> e.getValue().split("[\\p{Punct}\\s&&[^'-]]+")).
         flatMap((Serializable & Function<String[], Stream<String>>) Arrays::stream).
         collect(serializableCollector(() -> Collectors.groupingBy(Function.identity(), Collectors.counting())));
      this.result = (Map<Object, Object>) result;
      return result;
   }

   @Override
   public String getPrintableResult() {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<Object, Object> entry : result.entrySet()) {
         sb.append(System.getProperty("line.separator"));
         sb.append("key: " + entry.getKey() + " value: " + entry.getValue());
      }
      return sb.toString();
   }

   @Override
   public long getResultCount() {
      return result.keySet().size();
   }

   @FunctionalInterface
   private interface SerializableSupplier<T> extends Supplier<T>, Serializable {
   }

   public static <T, R> Collector<T, ?, R> serializableCollector(SerializableSupplier<Collector<T, ?, R>> supplier) {
      return new CollectorSupplier<>(supplier);
   }

   private static final class CollectorSupplier<T, R> implements Serializable, Collector<T, Object, R> {
      private final Supplier<Collector<T, ?, R>> supplier;
      private transient Collector<T, Object, R> collector;

      CollectorSupplier(Supplier<Collector<T, ?, R>> supplier) {
         this.supplier = supplier;
      }

      private Collector<T, Object, R> getCollector() {
         if (collector == null) {
            collector = (Collector<T, Object, R>) supplier.get();
         }
         return collector;
      }

      @Override
      public Supplier<Object> supplier() {
         return getCollector().supplier();
      }

      @Override
      public BiConsumer<Object, T> accumulator() {
         return getCollector().accumulator();
      }

      @Override
      public BinaryOperator<Object> combiner() {
         return getCollector().combiner();
      }

      @Override
      public Function<Object, R> finisher() {
         return getCollector().finisher();
      }

      @Override
      public Set<Characteristics> characteristics() {
         return getCollector().characteristics();
      }
   }
}

package org.radargun.stages.helpers;

import java.io.Serializable;

/**
 * The policy for selecting buckets in test
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class BucketPolicy implements Serializable {
   public static final String LAST_BUCKET = "LAST_BUCKET";

   public enum Type {
      NONE,
      THREAD,
      ALL
   }

   private Type type;
   private String arg;

   public BucketPolicy(Type type, String arg) {
      this.type = type;
      this.arg = arg;
   }

   public String getBucketName(int threadIndex) {
      switch (type) {
         case NONE: return null;
         case THREAD: return "bucket_" + threadIndex;
         case ALL: return arg;
      }
      throw new IllegalStateException();
   }

   @Override
   public String toString() {
      return "BucketPolicy(" + new Converter().convertToString(this) + ")";
   }

   public static class Converter implements org.radargun.config.Converter<BucketPolicy> {
      @Override
      public BucketPolicy convert(String string, java.lang.reflect.Type type) {
         int index = string.indexOf(':');
         if (index < 0) {
            return new BucketPolicy(Type.valueOf(string.toUpperCase()), null);
         } else {
            return new BucketPolicy(Type.valueOf(string.substring(0, index).toUpperCase()), string.substring(index + 1));
         }
      }

      @Override
      public String convertToString(BucketPolicy value) {
         return value.arg == null ? value.type.name() : value.type.name() + ":" + value.arg;
      }

      @Override
      public String allowedPattern(java.lang.reflect.Type type) {
         StringBuilder sb = new StringBuilder("(");
         Type[] values = Type.values();
         for (int i = 0; i < values.length - 1; ++i) {
            sb.append(values[i].name().toLowerCase()).append('|');
         }
         sb.append(")(:.*)?");
         return sb.toString();
      }
   }
}

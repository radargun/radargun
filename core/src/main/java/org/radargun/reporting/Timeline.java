package org.radargun.reporting;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.radargun.utils.TimeService;

/**
 * Events that should be presented in report
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Timeline implements Serializable, Comparable<Timeline> {

   public final int workerIndex;
   /* Events plotted on all charts as marker events. */
   private Map<String, List<MarkerEvent>> events = new HashMap<>();
   /* Values plotted in separate charts */
   private Map<Category, List<Value>> values = new HashMap<>();
   private long firstTimestamp = Long.MAX_VALUE;
   private long lastTimestamp = Long.MIN_VALUE;

   public Timeline(int workerIndex) {
      this.workerIndex = workerIndex;
   }

   public synchronized void addEvent(String category, MarkerEvent e) {
      List<MarkerEvent> cat = events.get(category);
      if (cat == null) {
         cat = new ArrayList<>();
         events.put(category, cat);
      }
      cat.add(e);
      updateTimestamps(e);
   }

   public synchronized void addValue(Category category, Value e) {
      List<Value> cat = values.get(category);
      if (cat == null) {
         cat = new ArrayList<>();
         values.put(category, cat);
      }
      cat.add(e);
      updateTimestamps(e);
   }

   public boolean containsValuesOfType(Category.Type type) {
      return values.keySet().stream().anyMatch(e -> e.getType().equals(type));
   }

   public static class Category implements Serializable, Comparable<Category> {
      private final String name;
      private final Type type;

      private Category(String name, Type type) {
         this.name = name;
         this.type = type;
      }

      @Override
      public int compareTo(Category o) {
         return this.getName().compareTo(o.getName());
      }

      public enum Type {
         /* All events related to system resources (CPU, memory, network, etc.) */
         SYSMONITOR,
         /* Any other type of events, e.g. recording values in background stages */
         CUSTOM
      }

      public static Category sysCategory(String name) {
         return new Category(name, Type.SYSMONITOR);
      }

      public static Category customCategory(String name) {
         return new Category(name, Type.CUSTOM);
      }

      public String getName() {
         return name;
      }

      public Type getType() {
         return type;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;

         Category category = (Category) o;

         if (!name.equals(category.name)) return false;
         return type == category.type;

      }

      @Override
      public int hashCode() {
         int result = name.hashCode();
         result = 31 * result + type.hashCode();
         return result;
      }
   }

   private void updateTimestamps(MarkerEvent e) {
      firstTimestamp = Math.min(firstTimestamp, e.getStarted());
      lastTimestamp = Math.max(lastTimestamp, e.getEnded());
   }

   private void updateTimestamps(Value v) {
      firstTimestamp = Math.min(firstTimestamp, v.getStarted());
      lastTimestamp = Math.max(lastTimestamp, v.getEnded());
   }

   public synchronized Set<String> getEventCategories() {
      return events.keySet();
   }

   public synchronized Set<Category> getValueCategories() {
      return values.keySet();
   }

   public synchronized List<MarkerEvent> getEvents(String category) {
      return events.get(category);
   }

   public synchronized List<Value> getValues(Category category) {
      return values.get(category);
   }

   public long getFirstTimestamp() {
      return firstTimestamp;
   }

   public long getLastTimestamp() {
      return lastTimestamp;
   }

   @Override
   public int compareTo(Timeline o) {
      return Integer.compare(workerIndex, o.workerIndex);
   }


   /**
    * A single value in the chart in time, such as CPU utilization. The value is reported
    * in a single chart dedicated for this type of values.
    */
   public static class Value implements Serializable, Comparable<MarkerEvent> {
      public final Number value;
      public final long timestamp;

      public Value(long timestamp, Number value) {
         this.timestamp = timestamp;
         this.value = value;
      }

      public Value(Number value) {
         this.timestamp = TimeService.currentTimeMillis();
         this.value = value;
      }

      @Override
      public String toString() {
         // doubles require %f, integers %d -> we use %s
         return String.format("Value{timestamp=%d, value=%s}", timestamp, value);
      }

      @Override
      public int compareTo(MarkerEvent o) {
         return Long.compare(timestamp, o.timestamp);
      }

      public long getStarted() {
         return timestamp;
      }

      public long getEnded() {
         return timestamp;
      }
   }

   /**
    * Generic event in timeline
    */
   public abstract static class MarkerEvent implements Serializable, Comparable<MarkerEvent> {
      public final long timestamp;

      protected MarkerEvent(long timestamp) {
         this.timestamp = timestamp;
      }

      protected MarkerEvent() {
         this(TimeService.currentTimeMillis());
      }

      @Override
      public int compareTo(MarkerEvent o) {
         return Long.compare(timestamp, o.timestamp);
      }

      public long getStarted() {
         return timestamp;
      }

      public long getEnded() {
         return timestamp;
      }
   }

   /**
    * Occurence of this event is not a value in any series, such as worker crash.
    */
   public static class TextEvent extends MarkerEvent {
      public final String text;

      public TextEvent(long timestamp, String text) {
         super(timestamp);
         this.text = text;
      }

      public TextEvent(String text) {
         this.text = text;
      }

      @Override
      public String toString() {
         return String.format("TextEvent{timestamp=%d, text=%s}", timestamp, text);
      }
   }

   /**
    * MarkerEvent representing some continuous operation taking place for some period of time
    */
   public static class IntervalEvent extends MarkerEvent {
      public final String description;
      public final long duration; // milliseconds

      public IntervalEvent(long timestamp, String description, long duration) {
         super(timestamp);
         this.description = description;
         this.duration = duration;
      }

      public IntervalEvent(String description, long duration) {
         this.description = description;
         this.duration = duration;
      }

      @Override
      public long getEnded() {
         return timestamp + duration;
      }

      @Override
      public String toString() {
         return String.format("IntervalEvent{timestamp=%d, duration=%d, description=%s}",
            timestamp, duration, description);
      }
   }

   /**
    * Dummy class used for remote signalization
    */
   public static class Request implements Serializable {
   }
}

package org.radargun.reporting.html;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.radargun.config.Cluster;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;

/**
 * Presents {@link Timeline timelines} from all slaves and master.
 * Uses {@link TimelineChart} to generate image files.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class TimelineDocument extends HtmlDocument {
   private static final Log log = LogFactory.getLog(TimelineDocument.class);
   private final Configuration configuration;
   private final String configName;
   private final String title;
   private final Cluster cluster;
   private List<Timeline> timelines;
   private Map<Timeline.Category, Double> minValues = new HashMap<>();
   private Map<Timeline.Category, Double> maxValues = new HashMap<>();
   private Map<Timeline.Category, Integer> valueCategories = new TreeMap<>();
   private Map<String, Integer> eventCategories = new TreeMap<>();
   private Timeline.Category.Type categoryType;
   private long startTimestamp = Long.MAX_VALUE, endTimestamp = Long.MIN_VALUE;

   public TimelineDocument(Configuration configuration, String directory, String configName, String title, List<Timeline> timelines, Timeline.Category.Type categoryType, Cluster cluster) {
      super(directory, categoryType.toString() + "_timeline_" + configName + ".html", title + " Timeline");
      this.title = title;
      this.configuration = configuration;
      this.timelines = new ArrayList<>(timelines);
      this.categoryType = categoryType;
      Collections.sort(this.timelines);
      this.configName = configName;
      this.cluster = cluster;

      for (Timeline timeline : this.timelines) {
         startTimestamp = Math.min(startTimestamp, timeline.getFirstTimestamp());
         endTimestamp = Math.max(endTimestamp, timeline.getLastTimestamp());
         for (String category : timeline.getEventCategories()) {
            if (!eventCategories.containsKey(category)) {
               eventCategories.put(category, eventCategories.size());
            }
         }

         for (Timeline.Category category : timeline.getValueCategories()) {
            if (!valueCategories.containsKey(category)) {
               valueCategories.put(category, valueCategories.size());
            }

            List<Timeline.Value> values = timeline.getValues(category);
            double min = Long.MAX_VALUE, max = Long.MIN_VALUE;
            for (Timeline.Value value : values) {
               double d = value.value.doubleValue();
                  max = Math.max(max, d);
                  min = Math.min(min, d);
            }
            if (min <= max) {
               Double prevMin = minValues.get(category);
               Double prevMax = maxValues.get(category);
               minValues.put(category, prevMin == null ? min : Math.min(prevMin, min));
               maxValues.put(category, prevMax == null ? max : Math.max(prevMax, max));
            }
         }
      }
      // in order to show event categories, we need at least one value category
      if (valueCategories.isEmpty()) {
         Timeline.Category defaultCategory = Timeline.Category.sysCategory("&nbsp;");
         valueCategories.put(defaultCategory, 0);
         minValues.put(defaultCategory, 0d);
         maxValues.put(defaultCategory, 0d);
      }
   }

   @Override
   public String getTitle() {
      return title;
   }

   public String range(final Timeline.Category valueCategory, final int valueCategoryId) {
      Double min = minValues.get(valueCategory);
      if (min == null || min > 0) min = 0d;
      Double max = maxValues.get(valueCategory);
      if (max == null || max < 0) max = 0d;
      minValues.put(valueCategory, min);
      maxValues.put(valueCategory, max);

      return String.format("timeline_%s_%d_range.png", configName, valueCategoryId);
   }

   public String getValueChartFile(int valueCategoryId, int slaveIndex) {
      return String.format("timeline_%s_v%d_%d.png", configName, valueCategoryId, slaveIndex);
   }

   public Map<Timeline.Category, Integer> getValueCategoriesOfType(String categoryType) {
      return valueCategories.entrySet().stream().filter(e -> e.getKey().getType().toString().equals(categoryType)).collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue(), (v1,v2) ->{ throw new RuntimeException(String.format("Duplicate key for values %s and %s", v1, v2));},
         TreeMap::new));
   }

   public void createTestCharts() {
      createReportDirectory();

      final AtomicBoolean firstDomain = new AtomicBoolean(true);
      final String relativeDomainFile = "domain_" + configName + "_relative.png";
      final String absoluteDomainFile = "domain_" + configName + "_absolute.png";
      ArrayList<Future> chartTaskFutures = new ArrayList<>();
      for (Map.Entry<Timeline.Category, Integer> valueEntry : getValueCategoriesOfType(categoryType.toString()).entrySet()) {
         final Timeline.Category valueCategory = valueEntry.getKey();
         final int valueCategoryId = valueEntry.getValue();

         /* Range */
         final String rangeFile = range(valueCategory, valueCategoryId);

         /* Charts */
         final AtomicBoolean firstRange = new AtomicBoolean(true);
         for (Timeline timeline : timelines) {
            List<Timeline.Value> categoryValues = timeline.getValues(valueCategory);
            final List<Timeline.Value> values = categoryValues != null ? categoryValues : Collections.EMPTY_LIST;
            final int slaveIndex = timeline.slaveIndex;
            final String valueChartFile = getValueChartFile(valueCategoryId, slaveIndex);

            chartTaskFutures.add(HtmlReporter.executor.submit(new Callable<Void>() {
               @Override
               public Void call() throws Exception {
                  log.info("Generating chart for " + valueCategory);
                  TimelineChart chart = new TimelineChart();
                  chart.setDimensions(configuration.width, configuration.height);

                  chart.setEvents(values, slaveIndex, startTimestamp, endTimestamp, minValues.get(valueCategory) * 1.1, maxValues.get(valueCategory) * 1.1);


                  chart.saveChart(directory + File.separator + valueChartFile);

                  if (firstRange.compareAndSet(true, false)) {
                     chart.saveRange(directory + File.separator + rangeFile);
                  }
                  if (firstDomain.compareAndSet(true, false)) {
                     chart.saveRelativeDomain(directory + File.separator + relativeDomainFile);
                     chart.saveAbsoluteDomain(directory + File.separator + absoluteDomainFile);
                  }
                  return null;
               }
            }));
         }
      }

      for (Timeline timeline : timelines) {
         final int slaveIndex = timeline.slaveIndex;
         for (String ec : timeline.getEventCategories()) {
            final String eventCategory = ec;
            final List<Timeline.MarkerEvent> events = timeline.getEvents(eventCategory);
            if (events == null) continue;

            chartTaskFutures.add(HtmlReporter.executor.submit(new Callable<Void>() {
               @Override
               public Void call() throws Exception {
                  TimelineChart chart = new TimelineChart();
                  chart.setDimensions(configuration.width, configuration.height);
                  chart.setEvents(events, slaveIndex, startTimestamp, endTimestamp, 0, 0);

                  String chartFile = String.format("timeline_%s_e%d_%d.png", configName, eventCategories.get(eventCategory), slaveIndex);
                  chart.saveChart(directory + File.separator + chartFile);
                  return null;
               }
            }));
         }
      }
      /* wait until all charts are generated */

      for (Future f : chartTaskFutures) {
         try {
            f.get();
         } catch (Exception e) {
            log.error("Failed to generate on of the charts: ", e);
         }
      }
   }

   /**
    * The following methods are used in Freemarker templates
    * e.g. method getPercentiles() can be used as getPercentiles() or percentiles in template
    */

   public Configuration getConfiguration() {
      return configuration;
   }

   public String getConfigName() {
      return configName;
   }

   public Cluster getCluster() {
      return cluster;
   }

   public List<Timeline> getTimelines() {
      return timelines;
   }

   public Map<Timeline.Category, Double> getMinValues() {
      return minValues;
   }

   public Map<Timeline.Category, Double> getMaxValues() {
      return maxValues;
   }

   public Map<Timeline.Category, Integer> getValueCategories() {
      return valueCategories;
   }

   public Map<String, Integer> getEventCategories() {
      return eventCategories;
   }

   public long getStartTimestamp() {
      return startTimestamp;
   }

   public long getEndTimestamp() {
      return endTimestamp;
   }

   public String generateEventChartFile(int eventCategoryId, int slaveIndex) {
      return String.format("timeline_%s_e%d_%d.png", configName, eventCategoryId, slaveIndex);
   }

   public String getCheckboxColor(Timeline timeline) {
      return String.format("#%06X", TimelineChart.getColorForIndex(timeline.slaveIndex));
   }

   public static class Configuration {
      @Property(name = "chart.width", doc = "Width of the chart in pixels. Default is 1024.")
      private int width = 1024;

      @Property(name = "chart.height", doc = "Height of the chart in pixels. Default is 500.")
      private int height = 500;

      /**
       * The following methods are used in Freemarker templates
       * e.g. method getPercentiles() can be used as getPercentiles() or percentiles in template
       */

      public int getWidth() {
         return width;
      }

      public int getHeight() {
         return height;
      }
   }
}

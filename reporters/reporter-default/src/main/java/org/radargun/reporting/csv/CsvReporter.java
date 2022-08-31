package org.radargun.reporting.csv;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.radargun.config.Converter;
import org.radargun.config.MainConfig;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.AbstractReporter;
import org.radargun.reporting.Report;
import org.radargun.reporting.Timeline;
import org.radargun.reporting.commons.DataReporter;

/**
 * Reporter producing CSV files.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class CsvReporter extends AbstractReporter {

   protected static final Log log = LogFactory.getLog(CsvReporter.class);

   @Property(doc = "Directory into which will be report files written.")
   private String targetDir = "results" + File.separator + "csv";

   @Property(doc = "Workers whose results will be ignored.")
   private Set<Integer> ignore;

   @Property(doc = "Separator of columns in the CSV file. Default is ','")
   private String separator = ",";

   @Property(doc = "Compute aggregated statistics from all nodes. Default is true.")
   private boolean computeTotal = true;

   @Property(doc = "Compute response times at certain percentiles. Default is 95% and 99%.")
   protected double[] percentiles = new double[] {95d, 99d};

   @Property(doc = "List od comma separated column name regex patterns which should be reordered to the left side, use '\\' to escape the commas if needed", converter = RegexConverter.class)
   protected List<String> columnOrder = Arrays.asList(".*[Put|Get]\\.Throughput", ".*[Put|Get]\\.ResponseTimeMean", ".*Get.*");

   @Override
   public void run(MainConfig mainConfig, Collection<Report> reports) {
      for (Report report : reports) {
         for (Report.Test test : report.getTests()) {
            reportTest(report, test);
         }
         reportTimelines(report);
      }
   }

   private void reportTest(Report report, Report.Test test) {
      FileWriter fileWriter = null;
      try {
         DataReporter.DataReportValue value = DataReporter.get(test, ignore, computeTotal, percentiles);
         fileWriter = new FileWriter(DataReporter.prepareOutputFile(report, test.name, "", targetDir, "csv"));
         writeFile(fileWriter, value.columns, value.rows);
      } catch (IOException e) {
         log.error("Failed to create report for test " + test.name, e);
      } finally {
         if (fileWriter != null) {
            try {
               fileWriter.close();
            } catch (IOException e) {
               log.error("Failed to close", e);
            }
         }
      }
   }

   private void writeFile(FileWriter fileWriter, Set<String> columns, List<Map<String, String>> rows)
         throws IOException {

      List<String> indexColumns = Arrays.asList(DataReporter.ITERATION, DataReporter.WORKER_INDEX, DataReporter.PERIOD, DataReporter.THREAD_COUNT);

      List<String> orderedColumns = new ArrayList<String>(indexColumns);
      orderedColumns.addAll(columns);

      List<String> columnOrderList = new ArrayList<String>(indexColumns);
      columnOrderList.addAll(columnOrder);

      // sorts columns into correct order
      orderedColumns.sort(new ColumnComparator(columnOrderList));

      // sorts rows into correct order
      rows.sort(new RowComparator(Arrays.asList(DataReporter.WORKER_INDEX, DataReporter.ITERATION, DataReporter.PERIOD, DataReporter.THREAD_COUNT)));

      for (String column : orderedColumns) {
         fileWriter.write(column);
         fileWriter.write(separator);
      }

      fileWriter.write("\n");
      for (Map<String, String> row : rows) {
         for (String column : orderedColumns) {
            String value = row.get(column);
            if (value != null)
               fileWriter.write(value);
            fileWriter.write(separator);
         }
         fileWriter.write("\n");
      }
   }

   private static class ValueAndWorker implements Comparable<ValueAndWorker> {
      Timeline.Value value;
      int workerIndex;

      private ValueAndWorker(Timeline.Value value, int workerIndex) {
         this.value = value;
         this.workerIndex = workerIndex;
      }

      @Override
      public int compareTo(ValueAndWorker o) {
         int c = Long.compare(value.timestamp, o.value.timestamp);
         return c == 0 ? Integer.compare(workerIndex, o.workerIndex) : c;
      }
   }

   private void reportTimelines(Report report) {
      Set<Timeline.Category> allCategories = new HashSet<>();
      int maxWorkerIndex = 0;
      for (Timeline t : report.getTimelines()) {
         allCategories.addAll(t.getValueCategories());
         maxWorkerIndex = Math.max(maxWorkerIndex, t.workerIndex);
      }
      for (Timeline.Category valueCategory : allCategories) {
         FileWriter writer = null;
         try {
            writer = new FileWriter(DataReporter.prepareOutputFile(report, "timeline", "_" + valueCategory.getName(), targetDir, "csv"));
            writer.write("Timestamp");
            writer.write(separator);
            for (int i = 0; i <= maxWorkerIndex; ++i) {
               writer.write(String.format("Worker %d%s", i, separator));
            }
            writer.write('\n');
            List<ValueAndWorker> values = new ArrayList<ValueAndWorker>();
            for (Timeline t : report.getTimelines()) {
               List<Timeline.Value> list = t.getValues(valueCategory);
               if (list == null)
                  continue;
               for (Timeline.Value v : list) {
                  values.add(new ValueAndWorker(v, t.workerIndex));
               }
            }
            Collections.sort(values);
            long currTimestamp = Long.MIN_VALUE;
            int nextIndex = -1;
            for (ValueAndWorker vas : values) {
               if (currTimestamp != vas.value.timestamp) {
                  if (nextIndex >= 0) {
                     for (int i = nextIndex; i <= maxWorkerIndex; ++i) {
                        writer.write(separator);
                     }
                     writer.write('\n');
                  }
                  nextIndex = 0;
                  writer.write(String.format("%d%s", vas.value.timestamp, separator));
               }
               for (int i = nextIndex; i < vas.workerIndex; ++i) {
                  writer.write(separator);
               }
               writer.write(vas.value.value.toString());
               writer.write(separator);
               currTimestamp = vas.value.timestamp;
               nextIndex = vas.workerIndex + 1;
            }
         } catch (IOException e) {
            log.error("Failed to create timeline report for category " + valueCategory.getName(), e);
         } finally {
            if (writer != null) {
               try {
                  writer.close();
               } catch (IOException e) {
                  log.error("Failed to close", e);
               }
            }
         }
      }
   }

   /**
    * Comparator for sorting rows based on column value
    * 
    * @author zhostasa
    *
    */
   private class RowComparator implements Comparator<Map<String, String>> {

      private List<String> comparePriority;

      /**
       * Instantiates comparator
       * 
       * @param comparePriority
       *           List of columns names to sort by, priority is defined by
       *           order in list
       */
      public RowComparator(List<String> comparePriority) {
         this.comparePriority = comparePriority;
      }

      public int compare(Map<String, String> o1, Map<String, String> o2) {
         for (String compareColumn : comparePriority) {
            int comp = o1.get(compareColumn).compareTo(o2.get(compareColumn));
            if (comp != 0)
               return comp;
         }
         return 0;
      }
   }

   /**
    * Comparator for sorting columns by matching regex
    * 
    * @author zhostasa
    *
    */
   private class ColumnComparator implements Comparator<String> {

      private List<String> compareRegex;

      /**
       * Instantiates comparator
       * 
       * @param compareRegex
       *           List of regular expressions to sort by, priority is
       *           determined by order in list
       */
      public ColumnComparator(List<String> compareRegex) {
         this.compareRegex = compareRegex;
      }

      public int compare(String o1, String o2) {

         for (String regex : compareRegex) {
            boolean o1Matches = o1.matches(regex);
            boolean o2Matches = o2.matches(regex);
            if (o1Matches && !o2Matches)
               return -1;
            if (!o1Matches && o2Matches)
               return 1;
         }
         return 0;
      }
   }

   /**
    * A converter for a comma separated list of regular expression arguments,
    * '\\' is used to escape the commas
    * 
    * @author zhostasa
    *
    */
   public static class RegexConverter implements Converter<List<String>> {

      public List<String> convert(String string, Type type) {
         List<String> newList = Arrays.asList(string.split("(?<!\\\\),"));
         return newList;
      }

      public String convertToString(List<String> value) {
         return value.stream().map(n -> n.replace(",", "\\,")).collect(Collectors.joining(","));
      }

      public String allowedPattern(Type type) {
         return ".*";
      }

   }
}

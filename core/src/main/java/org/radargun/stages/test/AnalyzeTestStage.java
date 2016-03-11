package org.radargun.stages.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.radargun.DistStageAck;
import org.radargun.config.DocumentedValue;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stats.OperationStats;
import org.radargun.stats.Statistics;
import org.radargun.stats.representation.RepresentationType;
import org.radargun.utils.Utils;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Analyzes results of already executed test.")
public class AnalyzeTestStage extends AbstractDistStage {
   @Property(doc = "Name of the test whose result should be analyzed.", optional = false)
   protected String testName;

   @Property(doc = "Operation that should be analyzed (e.g. BasicOperations.Get).", optional = false)
   protected String operation;

   @Property(doc = "What should be results of this analysis. Default is VALUE.")
   protected ResultType resultType = ResultType.VALUE;

   @Property(doc = "Name of the target property where the result should be stored.", optional = false)
   protected String storeResultTo;

   @Property(doc = "How should the thread statistics be aggregated. By default all statistics are merged.")
   protected ThreadGrouping threadGrouping = ThreadGrouping.GROUP_ALL;

   @Property(doc = "Which iterations should be included in the analysis. By default we iterate over all iterations.")
   protected IterationSelection iterationSelection = IterationSelection.EACH_ITERATION;

   @Property(doc = "How do we process the data. We can search for maximum, minimum or average.", optional = false)
   protected AnalyzisType analyzisType;

   @Property(doc = "What value do we we retrieve from the statistics.", optional = false, complexConverter = RepresentationType.ComplexConverter.class)
   protected RepresentationType statisticsType;

   @Override
   public Map<String, Object> createMasterData() {
      Report.Test test = masterState.getReport().getTest(testName);
      if (test == null) throw new IllegalArgumentException("No test '" + testName + "' found.");
      Number result = analyze(test);
      log.infof("Result of analysis is %s, storing into %s", result, storeResultTo);
      masterState.put(storeResultTo, result);
      return Collections.singletonMap(storeResultTo, (Object) result);
   }

   protected Number analyze(Report.Test test) {
      List<Group> groups = group(test);
      log.infof("Grouped test results into %d groups", groups.size());
      if (groups.size() == 0) return Double.NaN;
      double min = Double.MAX_VALUE, max = -Double.MAX_VALUE, sum = 0;
      Group minGroup = null, maxGroup = null;
      for (Group g : groups) {
         double value = statisticsType.getValue(g.stats, g.duration);
         log.tracef("iteration %d, node %d, thread %d: %d threads, duration %s -> value %f",
            g.origin.iteration, g.origin.node, g.origin.thread, g.threads,
            Utils.prettyPrintTime(g.duration, TimeUnit.NANOSECONDS), value);
         if (value < min) {
            min = value;
            minGroup = g;
         }
         if (value > max) {
            max = value;
            maxGroup = g;
         }
         sum += value;
      }
      switch (analyzisType) {
         case MAX:
            return getResult(max, maxGroup);
         case MIN:
            return getResult(min, minGroup);
         case AVERAGE:
            if (resultType != ResultType.VALUE) {
               throw new IllegalArgumentException("Cannot compute average with result type different than VALUE");
            }
            return sum / groups.size();
      }
      throw new IllegalStateException("Unexpected analysis type: " + analyzisType);
   }

   private Number getResult(double value, Group group) {
      switch (resultType) {
         case VALUE:
            return value;
         case ITERATION:
            return group.origin.iteration;
         case NODE:
            if (threadGrouping == ThreadGrouping.GROUP_ALL) {
               throw new IllegalArgumentException("Cannot find node when grouping all results");
            }
            return group.origin.node;
         case THREAD:
            if (threadGrouping != ThreadGrouping.EACH_THREAD) {
               throw new IllegalArgumentException("Cannot find thread when grouping all results");
            }
            return group.origin.thread;
         default:
            throw new IllegalArgumentException("Unexpected result type: " + resultType);
      }
   }

   protected List<Group> group(Report.Test test) {
      switch (iterationSelection) {
         case EACH_ITERATION:
            List<Group> groups = new ArrayList<>();
            int iterationCounter = 0;
            for (Report.TestIteration it : test.getIterations()) {
               groups.addAll(group(iterationCounter++, it.getStatistics()));
            }
            return groups;
         case LAST_ITERATION:
            List<Report.TestIteration> iterations = test.getIterations();
            if (iterations.size() > 0) {
               int iteration = iterations.size() - 1;
               return group(iteration, iterations.get(iteration).getStatistics());
            } else {
               return Collections.EMPTY_LIST;
            }
         default:
            throw new IllegalStateException("Unexpected iteration grouping: " + iterationSelection);
      }
   }

   private List<Group> group(int iteration, Set<Map.Entry<Integer, List<Statistics>>> statistics) {
      List<Group> groups = new ArrayList<>();
      switch (threadGrouping) {
         case EACH_THREAD:
            for (Map.Entry<Integer, List<Statistics>> entry : statistics) {
               int threadCounter = 0;
               for (Map.Entry<Integer, List<Statistics>> e : statistics) {
                  if (e.getKey() < entry.getKey()) {
                     threadCounter += e.getValue().size();
                  }
               }
               for (Statistics s : entry.getValue()) {
                  groups.add(new Group(s.getOperationsStats().get(operation), 1, duration(s), new Origin(iteration, entry.getKey(), threadCounter)));
               }
            }
            break;
         case GROUP_BY_NODE:
            for (Map.Entry<Integer, List<Statistics>> entry : statistics) {
               entry.getValue().stream().reduce(Statistics.MERGE).map(aggregation ->
                  groups.add(new Group(aggregation.getOperationsStats().get(operation), entry.getValue().size(), duration(aggregation), new Origin(iteration, entry.getKey(), -1)))
               );
            }
            break;
         case GROUP_ALL:
            int threads = statistics.stream().mapToInt(e -> e.getValue().size()).sum();
            statistics.stream().flatMap(e -> e.getValue().stream()).reduce(Statistics.MERGE).map(aggregation ->
               groups.add(new Group(aggregation.getOperationsStats().get(operation), threads, duration(aggregation), new Origin(iteration, -1, -1)))
            );
            break;
         default:
            throw new IllegalStateException("Unexpected thread grouping: " + threadGrouping);
      }
      return groups;
   }

   private long duration(Statistics s) {
      return TimeUnit.MILLISECONDS.toNanos(s.getEnd() - s.getBegin());
   }

   @Override
   public DistStageAck executeOnSlave() {
      return successfulResponse();
   }

   protected static class Group {
      public final OperationStats stats;
      public final int threads;
      public final long duration;
      public final Origin origin;

      public Group(OperationStats stats, int threads, long duration, Origin origin) {
         this.stats = stats;
         this.threads = threads;
         this.duration = duration;
         this.origin = origin;
      }
   }

   protected static class Origin {
      public final int iteration;
      public final int node;
      public final int thread;

      public Origin(int iteration, int node, int thread) {
         this.iteration = iteration;
         this.node = node;
         this.thread = thread;
      }
   }

   public enum IterationSelection {
      @DocumentedValue("The analysis will run on all iterations.")
      EACH_ITERATION,
      @DocumentedValue("Only the last iteration will be analyzed.")
      LAST_ITERATION
   }

   public enum ThreadGrouping {
      @DocumentedValue("Consider statistics of each thread.")
      EACH_THREAD,
      @DocumentedValue("Merge statistics of all thread on one node and analyze the result value.")
      GROUP_BY_NODE,
      @DocumentedValue("Merge statistics of all thread on all nodes and analyze the result value.")
      GROUP_ALL
   }

   public enum AnalyzisType {
      @DocumentedValue("Compute maximal value from all the results.")
      MAX,
      @DocumentedValue("Compute minimal value from all the results.")
      MIN,
      @DocumentedValue("Compute average value from all the results.")
      AVERAGE
   }

   public enum ResultType {
      @DocumentedValue("Report directly the value that was computed during this analysis.")
      VALUE,
      @DocumentedValue("Report the iteration number where we have found the desired value. Works for analyzis-type MAX or MIN.")
      ITERATION,
      @DocumentedValue("Report the node (slave index) where we have found the desired value. Works for analyzis-type MAX or MIN.")
      NODE,
      @DocumentedValue("Report the global thread id where we have found the desired value. Works for analyzis-type MAX or MIN.")
      THREAD
   }
}

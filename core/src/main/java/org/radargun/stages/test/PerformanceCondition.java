package org.radargun.stages.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.radargun.config.DefinitionElement;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stats.OperationStats;
import org.radargun.stats.Statistics;
import org.radargun.stats.representation.DefaultOutcome;
import org.radargun.stats.representation.OperationThroughput;
import org.radargun.utils.NanoTimeConverter;
import org.radargun.utils.Projections;
import org.radargun.utils.ReflexiveConverters;
import org.radargun.utils.Utils;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class PerformanceCondition {
   private static Log log = LogFactory.getLog(PerformanceCondition.class);

   public abstract boolean evaluate(Statistics statistics);

   private static class Predicate implements Projections.Condition<PerformanceCondition> {
      private final Statistics statistics;

      public Predicate(Statistics statistics) {
         this.statistics = statistics;
      }

      @Override
      public boolean accept(PerformanceCondition cond) {
         return cond.evaluate(statistics);
      }
   }

   @DefinitionElement(name = "any", doc = "Any of inner conditions is true", resolveType = DefinitionElement.ResolveType.PASS_BY_DEFINITION)
   protected static class Any extends PerformanceCondition {
      @Property(name = "", doc = "Inner conditions", complexConverter = ListConverter.class)
      public final List<PerformanceCondition> subs = new ArrayList<>();

      @Override
      public boolean evaluate(final Statistics statistics) {
         return Projections.any(subs, new Predicate(statistics));
      }

      @Override
      public String toString() {
         StringBuilder sb = new StringBuilder("any [");
         if (!subs.isEmpty()) sb.append(subs.get(0));
         for (int i = 1; i < subs.size(); ++i) sb.append(", ").append(subs.get(i));
         return sb.append("]").toString();
      }
   }

   @DefinitionElement(name = "all", doc = "All inner conditions are true", resolveType = DefinitionElement.ResolveType.PASS_BY_DEFINITION)
   protected static class All extends PerformanceCondition {
      @Property(name = "", doc = "Inner conditions", complexConverter = ListConverter.class)
      public final List<PerformanceCondition> subs = new ArrayList<>();

      @Override
      public boolean evaluate(final Statistics statistics) {
         return Projections.all(subs, new Predicate(statistics));
      }

      @Override
      public String toString() {
         StringBuilder sb = new StringBuilder("all [");
         if (!subs.isEmpty()) sb.append(subs.get(0));
         for (int i = 1; i < subs.size(); ++i) sb.append(", ").append(subs.get(i));
         return sb.append("]").toString();
      }
   }

   protected static abstract class AbstractCondition extends PerformanceCondition {
      @Property(doc = "Identifier of the operation (or its derivate) that should be tested.", optional = false)
      protected String on;

      @Override
      public String toString() {
         return this.getClass().getSimpleName() + PropertyHelper.toString(this);
      }
   }

   @DefinitionElement(name = "mean", doc = "Checks value of the mean response time of given operation.")
   protected static class Mean extends AbstractCondition {
      @Property(doc = "Test if the mean response time is below specified value (use time unit!)", converter = NanoTimeConverter.class)
      protected Long below;

      @Property(doc = "Test if the mean response time is above specified value (use time unit!)", converter = NanoTimeConverter.class)
      protected Long over;

      @Init
      public void init() {
         if (below != null && over != null) throw new IllegalStateException("Cannot define both 'below' and 'over'!");
         if (below == null && over == null) throw new IllegalStateException("Must define either 'below' or 'over'!");
      }

      @Override
      public boolean evaluate(Statistics statistics) {
         OperationStats stats = statistics.getOperationsStats().get(on);
         if (stats == null) throw new IllegalStateException("No statistics for operation " + on);
         DefaultOutcome outcome = stats.getRepresentation(DefaultOutcome.class);
         if (outcome == null) throw new IllegalStateException("Cannot determine mean from " + stats);
         log.info("Mean is " + Utils.prettyPrintTime((long) outcome.responseTimeMean, TimeUnit.NANOSECONDS) + PropertyHelper.toString(this));
         if (below != null) return outcome.responseTimeMean < below;
         if (over != null) return outcome.responseTimeMean > over;
         throw new IllegalStateException();
      }
   }

   protected static abstract class ThroughputBase extends AbstractCondition {
      @Property(doc = "Test if the actual throughput is below specified value (operations per second)")
      protected Long below;

      @Property(doc = "Test if the actual throughput is above specified value (operations per second)")
      protected Long over;

      @Init
      public void init() {
         if (below != null && over != null) throw new IllegalStateException("Cannot define both 'below' and 'over'!");
         if (below == null && over == null) throw new IllegalStateException("Must define either 'below' or 'over'!");
      }

      @Override
      public boolean evaluate(Statistics statistics) {
         OperationStats stats = statistics.getOperationsStats().get(on);
         if (stats == null) throw new IllegalStateException("No statistics for operation " + on);
         org.radargun.stats.representation.OperationThroughput throughput = stats.getRepresentation(
               org.radargun.stats.representation.OperationThroughput.class,
               TimeUnit.MILLISECONDS.toNanos(statistics.getEnd() - statistics.getBegin()));
         if (throughput == null) throw new IllegalStateException("Cannot determine throughput from " + stats);
         log.info(getClass().getSimpleName() + " is " + getThroughput(throughput) + " ops/s " + PropertyHelper.toString(this));
         if (below != null) return getThroughput(throughput) < below;
         if (over != null) return getThroughput(throughput) > over;
         throw new IllegalStateException();
      }

      protected abstract double getThroughput(OperationThroughput throughput);
   }

   @DefinitionElement(name = "throughput-gross", doc = "Checks value of gross throughput of given operation.")
   protected static class GrossThroughput extends ThroughputBase {
      protected double getThroughput(OperationThroughput throughput) {
         return throughput.gross;
      }
   }

   @DefinitionElement(name = "throughput-net", doc = "Checks value of net throughput of given operation.")
   protected static class NetThroughput extends ThroughputBase {
      protected double getThroughput(OperationThroughput throughput) {
         return throughput.net;
      }
   }

   @DefinitionElement(name = "requests", doc = "Checks number of executed operations.")
   protected static class Requests extends AbstractCondition {
      @Property(doc = "Test if the number of executed operations is below this value.")
      protected Long below;

      @Property(doc = "Test if the number of executed operations is above this value.")
      protected Long over;

      @Init
      public void init() {
         if (below != null && over != null) throw new IllegalStateException("Cannot define both 'below' and 'over'!");
         if (below == null && over == null) throw new IllegalStateException("Must define either 'below' or 'over'!");
      }

      @Override
      public boolean evaluate(Statistics statistics) {
         OperationStats stats = statistics.getOperationsStats().get(on);
         if (stats == null) throw new IllegalStateException("No statistics for operation " + on);
         DefaultOutcome outcome = stats.getRepresentation(DefaultOutcome.class);
         if (outcome == null) throw new IllegalStateException("Cannot determine request count from " + stats);
         log.info("Executed " + outcome.requests + " reqs " + PropertyHelper.toString(this));
         if (below != null) return outcome.requests < below;
         if (over != null) return outcome.requests > over;
         throw new IllegalStateException();
      }
   }

   @DefinitionElement(name = "errors", doc = "Checks number of executed operations.")
   protected static class Errors extends AbstractCondition {
      @Property(doc = "Test if the percentage of errors (out of total number of requests) is below this value.")
      protected Integer percentBelow;

      @Property(doc = "Test if the percentage of errors (out of total number of requests) is above this value.")
      protected Integer percentOver;

      @Property(doc = "Test if the total number of errors is below this value.")
      protected Long totalBelow;

      @Property(doc = "Test if the total number of errors is above this value.")
      protected Long totalOver;

      @Init
      public void init() {
         int defs = 0;
         if (totalBelow != null) defs++;
         if (totalOver != null) defs++;
         if (percentBelow != null) defs++;
         if (percentOver != null) defs++;
         if (defs != 1) throw new IllegalStateException("Must define exactly one of 'total-below', 'total-over', 'percent-below', 'percent-over'");
      }

      @Override
      public boolean evaluate(Statistics statistics) {
         OperationStats stats = statistics.getOperationsStats().get(on);
         if (stats == null) throw new IllegalStateException("No statistics for operation " + on);
         DefaultOutcome outcome = stats.getRepresentation(DefaultOutcome.class);
         if (outcome == null) throw new IllegalStateException("Cannot determine error count from " + stats);
         log.info("Encountered " + outcome.errors + " errors " + PropertyHelper.toString(this));
         if (totalBelow != null) return outcome.errors < totalBelow;
         if (totalOver != null) return outcome.errors > totalOver;
         if (percentBelow != null) return outcome.errors * 100 < outcome.requests * percentBelow;
         if (percentBelow != null) return outcome.errors * 100 > outcome.requests * percentOver;
         throw new IllegalStateException();
      }
   }

   @DefinitionElement(name = "percentile", doc = "Checks value of the response time of given operation " +
         "at some percentile (0 = fastest operation, 100 = slowest operation).")
   protected static class Percentile extends AbstractCondition {
      @Property(doc = "Test if the response time is below specified value (use time unit!)", converter = NanoTimeConverter.class)
      protected Long below;

      @Property(doc = "Test if the response time is above specified value (use time unit!)", converter = NanoTimeConverter.class)
      protected Long over;

      @Property(doc = "Percentile used for the comparison: (0 = fastest operation, 100 = slowest operation)", optional = false)
      protected double value;

      @Init
      public void init() {
         if (below != null && over != null) throw new IllegalStateException("Cannot define both 'below' and 'over'!");
         if (below == null && over == null) throw new IllegalStateException("Must define either 'below' or 'over'!");
      }

      @Override
      public boolean evaluate(Statistics statistics) {
         OperationStats stats = statistics.getOperationsStats().get(on);
         if (stats == null) throw new IllegalStateException("No statistics for operation " + on);
         org.radargun.stats.representation.Percentile percentile
               = stats.getRepresentation(org.radargun.stats.representation.Percentile.class, value);
         if (percentile == null) throw new IllegalStateException("Cannot determine percentile from " + stats);
         log.info("Response time is " + Utils.prettyPrintTime((long) percentile.responseTimeMax, TimeUnit.NANOSECONDS) + PropertyHelper.toString(this));
         if (below != null) return percentile.responseTimeMax < below;
         if (over != null) return percentile.responseTimeMax > over;
         throw new IllegalStateException();
      }
   }

   public static class Converter extends ReflexiveConverters.ObjectConverter {
      public Converter() {
         super(new Class<?>[] { Any.class, All.class, Mean.class, GrossThroughput.class, NetThroughput.class, Requests.class, Errors.class, Percentile.class});
      }
   }

   protected static class ListConverter extends ReflexiveConverters.ListConverter {
      public ListConverter() {
         super(new Class<?>[] { Any.class, All.class, Mean.class, GrossThroughput.class, NetThroughput.class, Requests.class, Errors.class, Percentile.class});
      }
   }
}

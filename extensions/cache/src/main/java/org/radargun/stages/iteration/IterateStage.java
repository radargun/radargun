package org.radargun.stages.iteration;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.radargun.DistStageAck;
import org.radargun.Operation;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.test.Invocation;
import org.radargun.stages.test.legacy.LegacyStressor;
import org.radargun.stages.test.legacy.LegacyTestStage;
import org.radargun.stages.test.legacy.OperationLogic;
import org.radargun.state.SlaveState;
import org.radargun.stats.Request;
import org.radargun.stats.Statistics;
import org.radargun.traits.CacheInformation;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Iterable;
import org.radargun.utils.Utils;

@Stage(doc = "Iterates through all entries.")
public class IterateStage extends LegacyTestStage {
   @Property(doc = "Full class name of the filter used to iterate through entries. Default is none (accept all).")
   public String filterClass;

   @Property(doc = "Parameters for the filter (used to resolve its properties). No defaults.")
   public String filterParam;

   @Property(doc = "Full class name of the converter. Default is no converter (Map.Entry<K, V> is returned).")
   public String converterClass;

   @Property(doc = "Parameter for the converter (used to resolve its properties). No defaults.")
   public String converterParam;

   @Property(doc = "Name of the container (e.g. cache, DB table etc.) that should be iterated. Default is the default container.")
   public String containerName;

   @Property(doc = "Number of next() calls that are allowed to fail until we break the loop. Default is 100.")
   public int maxNextFailures = 100;

   @Property(doc = "Fail the stage if some of the stressors has failed. Default is true.")
   public boolean failOnFailedIteration = true;

   @Property(doc = "Fail when the number of elements iterated is not same. Default is true.")
   public boolean failOnUnevenElements = true;

   @Property(doc = "Fail when the number of elements is different than total size. Default is true if filter is not defined and false otherwise.")
   public Boolean failOnNotTotalSize;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   protected Iterable iterable;

   @InjectTrait
   protected CacheInformation info;

   @Override
   public void init() {
      super.init();
      failOnNotTotalSize = (filterClass == null);
   }

   @Override
   protected DistStageAck newStatisticsAck(List<LegacyStressor> stressors) {
      List<IterationResult> results = gatherResults(stressors, new IterationResultRetriever());
      return new IterationAck(slaveState, results,
         info != null ? info.getCache(containerName).getTotalSize() : -1);
   }

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMaster(acks);
      if (result.isError()) return result;

      Report.Test test = getTest(true); // test already created in super
      long prevTotalSize = -1;
      long totalMinElements = -1, totalMaxElements = -1;
      Map<Integer, Report.SlaveResult> slaveResults = new HashMap<>();
      if (test != null) {
         int testIteration = test.getIterations().size();
         String iterationValue = resolveIterationValue();
         if (iterationValue != null) {
            test.setIterationValue(testIteration, iterationValue);
         }
      }
      for (IterationAck ack : instancesOf(acks, IterationAck.class)) {
         if (test != null) {
            test.addStatistics(getTestIteration(), ack.getSlaveIndex(), ack.results.stream().map(r -> r.stats).collect(Collectors.toList()));
         }
         long slaveMinElements = -1, slaveMaxElements = -1;
         for (int i = 0; i < ack.results.size(); ++i) {
            IterationResult sr = ack.results.get(i);
            if (sr.failed) {
               result = failOnFailedIteration ? errorResult() : result;
               log.warnf("Slave %d, stressor %d has failed", ack.getSlaveIndex(), i);
            } else {
               if (sr.minElements != sr.maxElements) {
                  log.warnf("Slave %d, stressor %d reports %d .. %d elements",
                     ack.getSlaveIndex(), i, sr.minElements, sr.maxElements);
                  result = failOnUnevenElements ? errorResult() : result;
               }
               if (totalMinElements < 0) {
                  totalMinElements = sr.minElements;
                  totalMaxElements = sr.maxElements;
               } else if (totalMinElements != sr.minElements || totalMaxElements != sr.maxElements) {
                  log.warnf("Previous stressor reported %d .. %d elements but slave %d, stressor %d reports %d .. %d elements",
                     totalMinElements, totalMaxElements, ack.getSlaveIndex(), i, sr.minElements, sr.maxElements);
                  result = failOnUnevenElements ? errorResult() : result;
               }
               if (ack.totalSize >= 0 && (sr.minElements != ack.totalSize || sr.maxElements != ack.totalSize)) {
                  log.warnf("Slave %d stressor %d reports %d element but " +
                     "total size is %d", ack.getSlaveIndex(), i, sr.maxElements, ack.totalSize);
                  result = failOnNotTotalSize ? errorResult() : result;
               }
               totalMinElements = Math.min(totalMinElements, sr.minElements);
               totalMaxElements = Math.min(totalMaxElements, sr.maxElements);
               if (slaveMinElements < 0) {
                  slaveMinElements = sr.minElements;
                  slaveMaxElements = sr.maxElements;
               } else {
                  slaveMinElements = Math.min(slaveMinElements, sr.minElements);
                  slaveMaxElements = Math.min(slaveMaxElements, sr.maxElements);
               }
            }
         }
         if (prevTotalSize < 0) prevTotalSize = ack.totalSize;
         else if (prevTotalSize != ack.totalSize) {
            log.warnf("Previous total size was %d but slave %d reports total size %d", prevTotalSize, ack.getSlaveIndex(), ack.totalSize);
            result = failOnNotTotalSize ? errorResult() : result;
         }
         slaveResults.put(ack.getSlaveIndex(), new Report.SlaveResult(range(slaveMinElements, slaveMaxElements),
            slaveMinElements != slaveMaxElements));
      }
      if (test != null) {
         test.addResult(getTestIteration(), new Report.TestResult("Elements", slaveResults,
            range(totalMinElements, totalMaxElements), totalMinElements != totalMaxElements));
      }
      return result;
   }

   private String range(long min, long max) {
      return min == max ? String.valueOf(min) : String.format("%d .. %d", min, max);
   }

   @Override
   public OperationLogic getLogic() {
      return new Logic();
   }

   private class Logic extends OperationLogic {
      private Iterable.Filter filter;
      private Iterable.Converter converter;
      private boolean failed;
      private long minElements = -1;
      private long maxElements = -1;

      @Override
      public void init(LegacyStressor stressor) {
         super.init(stressor);
         if (filterClass != null) {
            filter = Utils.instantiateAndInit(filterClass, filterParam);
         }
         if (converterClass != null) {
            converter = Utils.instantiateAndInit(converterClass, converterParam);
         }
      }

      @Override
      public void run(Operation ignored) throws RequestException {
         Iterable.CloseableIterator iterator;
         try {
            iterator = (Iterable.CloseableIterator) stressor.makeRequest(new GetIterator(iterable, containerName, filter, converter));
         } catch (Exception e) {
            log.error("Failed to retrieve iterator.", e);
            failed = true;
            return;
         }
         int nextFailures = 0;
         long elements = 0;
         Request request = stressor.getStats().startRequest();
         while (!failed) {
            try {
               if (!(boolean) stressor.makeRequest(new HasNext(iterator))) break;
            } catch (Exception e) {
               failed = true;
               log.error("hasNext() failed", e);
               break;
            }
            try {
               stressor.makeRequest(new Next(iterator));
               elements++;
            } catch (Exception e) {
               log.error("next() failed", e);
               nextFailures++;
               if (nextFailures > maxNextFailures) {
                  failed = true;
                  break;
               }
            }
         }
         if (!failed) {
            if (minElements < 0 || elements < minElements) {
               minElements = elements;
            }
            if (maxElements < 0 || elements > maxElements) {
               maxElements = elements;
            }
         }
         try {
            iterator.close();
         } catch (IOException e) {
            log.error("Failed to close the iterator", e);
            failed = true;
         }
         request.succeeded(Iterable.FULL_LOOP);
      }
   }

   protected static class GetIterator implements Invocation {
      private final Iterable iterable;
      private final String containerName;
      private final Iterable.Filter filter;
      private final Iterable.Converter converter;

      public GetIterator(Iterable iterable, String containerName, Iterable.Filter filter, Iterable.Converter converter) {
         this.iterable = iterable;
         this.containerName = containerName;
         this.filter = filter;
         this.converter = converter;
      }

      @Override
      public Object invoke() {
         if (converter == null) {
            return iterable.getIterator(containerName, filter);
         } else {
            return iterable.getIterator(containerName, filter, converter);
         }
      }

      @Override
      public Operation operation() {
         return Iterable.GET_ITERATOR;
      }

      @Override
      public Operation txOperation() {
         return Iterable.GET_ITERATOR;
      }
   }

   protected static class HasNext implements Invocation {
      private final Iterator iterator;

      public HasNext(Iterator iterator) {
         this.iterator = iterator;
      }

      @Override
      public Object invoke() {
         return iterator.hasNext();
      }

      @Override
      public Operation operation() {
         return Iterable.HAS_NEXT;
      }

      @Override
      public Operation txOperation() {
         return Iterable.HAS_NEXT;
      }
   }

   protected static class Next implements Invocation {
      private final Iterator iterator;

      public Next(Iterator iterator) {
         this.iterator = iterator;
      }

      @Override
      public Object invoke() {
         return iterator.next();
      }

      @Override
      public Operation operation() {
         return Iterable.NEXT;
      }

      @Override
      public Operation txOperation() {
         return Iterable.NEXT;
      }
   }

   private static class IterationAck extends DistStageAck {
      private List<IterationResult> results;
      private long totalSize;

      public IterationAck(SlaveState slaveState, List<IterationResult> results, long totalSize) {
         super(slaveState);
         this.results = results;
         this.totalSize = totalSize;
      }
   }

   private static class IterationResult implements Serializable {
      private Statistics stats;
      private long minElements, maxElements;
      private boolean failed;

      private IterationResult(Statistics stats, long minElements, long maxElements, boolean failed) {
         this.stats = stats;
         this.minElements = minElements;
         this.maxElements = maxElements;
         this.failed = failed;
      }
   }

   private class IterationResultRetriever implements ResultRetriever<IterationResult> {
      @Override
      public IterationResult getResult(LegacyStressor stressor) {
         Logic logic = (Logic) stressor.getLogic();
         return new IterationResult(stressor.getStats(), logic.minElements, logic.maxElements, logic.failed);
      }

      @Override
      public IterationResult merge(IterationResult result1, IterationResult result2) {
         return new IterationResult(Statistics.MERGE.apply(result1.stats, result2.stats),
            Math.min(result1.minElements, result2.minElements),
            Math.max(result1.maxElements, result2.maxElements),
            result1.failed || result2.failed);
      }
   }
}

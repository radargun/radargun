package org.radargun.stats;

import java.util.concurrent.TimeUnit;

import org.radargun.config.DefinitionElement;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.utils.NanoTimeConverter;
import org.radargun.utils.TimeConverter;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@DefinitionElement(name = "common", doc = "Sampled basic statistics with histogram for the whole duration.")
public class CommonStatistics extends MultiStatistics {
   @Property(doc = "Duration of one sample in stats. Defaults to 1 second.", converter = TimeConverter.class)
   private long period = 1000;

   @Property(doc = "Maximum value that could be recorded in histogram. Default is one hour.", converter = NanoTimeConverter.class)
   private long maxValue = TimeUnit.HOURS.toNanos(1);

   @Property(doc = "Number of significant digits in histogram. Default is 2.")
   private int digits = 2;

   public CommonStatistics() {
      super(new Statistics[] { new BasicStatistics(), new PeriodicStatistics(), new BasicStatistics(new HistogramOperationStats())});
   }

   private CommonStatistics(Statistics[] internal) {
      super(internal);
   }

   @Init
   public void init() {
      ((PeriodicStatistics) internal[1]).setPeriod(period);
      ((HistogramOperationStats) ((BasicStatistics) internal[2]).prototype).init(maxValue, digits);
   }

   @Override
   protected MultiStatistics newInstance(Statistics[] internal) {
      return new CommonStatistics(internal);
   }

   @Override
   protected MultiStatistics copy(Statistics[] internalCopy) {
      return new CommonStatistics(internalCopy);
   }
}

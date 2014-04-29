package org.radargun.stats;

/**
 * Statistics allowing different OperationStats instance for each operation.
 * This is particularly useful for {@link HistogramOperationStats} which
 * need specific init arguments for each operation.
 *
 * TODO: eventually replace our histogram implementation with HdrHistogram
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class HistogramStatistics extends DefaultStatistics {

   private final OperationStats[] prototypes;

   public HistogramStatistics(OperationStats[] prototypes, OperationStats defaultPrototype) {
      super(defaultPrototype);
      this.prototypes = prototypes;
   }

   @Override
   protected OperationStats createOperationStats(int operationId) {
      if (operationId >= prototypes.length) return prototype.copy();
      return prototypes[operationId].copy();
   }
}

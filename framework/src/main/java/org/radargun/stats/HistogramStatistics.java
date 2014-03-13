package org.radargun.stats;

/**
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

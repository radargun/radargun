package org.cachebench.local;

import org.cachebench.utils.Utils;

/**
 * // TODO: Mircea - Document this!
 *
 * @author Mircea.Markus@jboss.com
 */
public class ReportItem {

   String product;
   String configuration;

   long readsPerSec;
   long writesPerSec;

   long noWrites;

   long noReads;


   public ReportItem(String product, String configuration) {
      this.product = product;
      this.configuration = configuration;
   }

   public String getConfiguration() {
      return configuration;
   }

   public void setConfiguration(String configuration) {
      this.configuration = configuration;
   }

   public long getReadsPerSec() {
      return readsPerSec;
   }

   public void setReadsPerSec(long readsPerSec) {
      this.readsPerSec = readsPerSec;
   }

   public long getWritesPerSec() {
      return writesPerSec;
   }

   public void setWritesPerSec(long writesPerSec) {
      this.writesPerSec = writesPerSec;
   }

   public long getNoWrites() {
      return noWrites;
   }

   public void setNoWrites(long noPuts) {
      this.noWrites = noPuts;
   }

   public long getNoReads() {
      return noReads;
   }

   public void setNoReads(long noReads) {
      this.noReads = noReads;
   }

   public boolean matches(String product, String config) {
      return this.product.equals(product) && this.configuration.equals(config);
   }

   public Comparable description() {
      return product + "(" + Utils.fileName2Config(configuration) + ")";
   }
}

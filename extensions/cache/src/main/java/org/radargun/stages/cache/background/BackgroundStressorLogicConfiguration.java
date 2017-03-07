package org.radargun.stages.cache.background;

import java.util.List;

import org.radargun.config.Property;
import org.radargun.stages.cache.generators.ByteArrayValueGenerator;
import org.radargun.stages.cache.generators.ValueGenerator;

/**
 * Configuration specific to {@link BackgroundStressorLogic}
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class BackgroundStressorLogicConfiguration {
   @Property(doc = "Size of value used in the entry. Default is 1024 bytes.")
   protected int entrySize = 1024;

   @Property(doc = "List of slaves where the data should be loaded (others immediately start executing requests). Default is all live slaves).")
   protected List<Integer> loadDataOnSlaves;

   @Property(doc = "List of slaves whose data should be loaded by other threads because these slaves are not alive. Default is empty.")
   protected List<Integer> loadDataForDeadSlaves;

   @Property(doc = "If set to true, the stressor does not execute any requests after loading the data. Default is false.")
   protected boolean loadOnly = false;

   @Property(doc = "Use conditional putIfAbsent instead of simple put for loading the keys. Default is false.")
   protected boolean loadWithPutIfAbsent = false;

   @Property(doc = "Use replace operations instead of puts during the test. Default is false.")
   protected boolean putWithReplace = false;

   @Property(doc = "Specifies whether the stage should wait until the entries are loaded by stressor threads. Default is true.")
   protected boolean waitUntilLoaded = true;

   @Property(doc = "Do not execute the loading, start usual request right away.")
   protected boolean noLoading = false;

   @Property(doc = "Generator of values. Default is byte-array.",
      complexConverter = ValueGenerator.ComplexConverter.class)
   protected ValueGenerator valueGenerator = new ByteArrayValueGenerator();

   public int getEntrySize() {
      return entrySize;
   }

   public List<Integer> getLoadDataOnSlaves() {
      return loadDataOnSlaves;
   }

   public List<Integer> getLoadDataForDeadSlaves() {
      return loadDataForDeadSlaves;
   }

   public boolean isLoadOnly() {
      return loadOnly;
   }

   public boolean isLoadWithPutIfAbsent() {
      return loadWithPutIfAbsent;
   }

   public boolean isWaitUntilLoaded() {
      return waitUntilLoaded;
   }

   public boolean isNoLoading() {
      return noLoading;
   }

   public boolean isPutWithReplace() {
      return putWithReplace;
   }

   public ValueGenerator getValueGenerator() {
      return valueGenerator;
   }
}

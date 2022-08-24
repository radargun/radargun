package org.radargun.stages.monitor;

import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.JmxConnectionProvider;

/**
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Periodically generates heap dumps.")
public class PeriodicHeapDumpStage extends PeriodicStage {
   protected static final String CLEANUP = PeriodicHeapDumpStage.class.getSimpleName() + "_CLEANUP";
   protected static final String FUTURE = PeriodicHeapDumpStage.class.getSimpleName() + "_FUTURE";

   @Property(doc = "Location on disk where the heap dumps should be stored.", optional = false)
   protected String dir;

   @Property(doc = "If set it only prints objects which have active references and discards the ones that are ready to be garbage collected")
   protected boolean live = false;

   @InjectTrait
   private JmxConnectionProvider jmxConnectionProvider;

   @Override
   public PeriodicTask createTask() {
      return new HeapDumpTask(jmxConnectionProvider, dir, workerState.getConfigName(), workerState.getWorkerIndex(), live);
   }

   @Override
   public String getFutureKey() {
      return FUTURE;
   }

   @Override
   public String getCleanupKey() {
      return CLEANUP;
   }
}

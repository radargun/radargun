package org.radargun;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.radargun.state.MainState;
import org.radargun.state.WorkerState;

/**
 * Defines an stage that will be run on both main and workers.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public interface DistStage extends Stage, Serializable {

   /**
    * Initialize the stage on main node.
    * @param mainState
    */
   void initOnMain(MainState mainState);

   /**
    * Creates custom data that should be passed to the worker and inserted into its state before
    * the stage is resolved. Those values can be used both for property resolution (the values
    * can be then stringified) or retrieved through {@link WorkerState#get(String)} later during
    * {@link #executeOnWorker()}.
    * @return Map of variable names to objects.
    */
   Map<String, Object> createMainData();

   /**
    * Initialize the stage on worker node. The stage must not use injected traits in this method.
    * @param workerState
    */
   void initOnWorker(WorkerState workerState);

   /**
    * Do whatever on the worker. This will only be called after {@link #initOnWorker(org.radargun.state.WorkerState)} is called.
    * @return an response that will be serialized and send back to the main.
    */
   DistStageAck executeOnWorker();

   /**
    * After all workers replied through {@link #executeOnWorker()}, this method will be called on the main.
    * @return returning false will cause the benchmark to stop.
    */
   StageResult processAckOnMain(List<DistStageAck> acks);

   //TODO: remove the call from stages

   /**
    * Should this stage be executed, based on its properties?
    * @return
    */
   boolean shouldExecute();
}

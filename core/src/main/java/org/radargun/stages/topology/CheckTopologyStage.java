package org.radargun.stages.topology;

import java.util.Date;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.config.TimeConverter;
import org.radargun.stages.AbstractDistStage;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.TopologyHistory;
import org.radargun.traits.TopologyHistory.Event;

/**
 * Controls which topology events have (not) happened recently
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Controls which topology events have (not) happened recently")
public class CheckTopologyStage extends AbstractDistStage {

   enum Type {
      HASH_AND_TOPOLOGY,
      HASH,
      TOPOLOGY
   }

   @Property(doc = "What does this stage control. Default is both DataRehashed and TopologyChanged events.")
   private Type type = Type.HASH_AND_TOPOLOGY;

   @Property(converter = TimeConverter.class, doc = "The period in milliseconds which is checked. Default is infinite.")
   private long period = Long.MAX_VALUE;

   @Property(doc = "The check controls if this event has happened (true) or not happened (false). Defaults to true.")
   private boolean changed = true;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private TopologyHistory topologyHistory;

   @Override
   public DistStageAck executeOnSlave() {
      if (!shouldExecute()) {
         log.debug("Ignoring this slave");
         return successfulResponse();
      }
      if (type == Type.HASH_AND_TOPOLOGY || type == Type.TOPOLOGY) {
         List<Event> history = topologyHistory.getTopologyChangeHistory();
         if (!check(history)) {
            return errorResponse("Topology check failed, " + (history.isEmpty() ? "no change in history" : "last change " + history.get(history.size() - 1)));
         } else {
            log.debug("Topology check passed.");
         }
      }
      if (type == Type.HASH_AND_TOPOLOGY || type == Type.HASH) {
         List<Event> history = topologyHistory.getRehashHistory();
         if (!check(history)) {
            return errorResponse("Hash check failed, " + (history.isEmpty() ? "no change in history" : "last change " + history.get(history.size() - 1)));
         } else {
            log.debug("Hash check passed.");
         }
      }
      return successfulResponse();
   }

   private boolean check(List<Event> history) {
      if (history.isEmpty()) return !changed;
      long lastChange = history.get(history.size() - 1).getEnded().getTime();
      long current = new Date().getTime();
      boolean hasChanged = lastChange + period > current; 
      return hasChanged == changed;
   }
}

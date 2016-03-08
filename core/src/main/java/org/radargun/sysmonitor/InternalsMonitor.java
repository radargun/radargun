package org.radargun.sysmonitor;

import java.util.Map;

import org.radargun.reporting.Timeline;
import org.radargun.traits.InternalsExposition;
import org.radargun.utils.TimeService;

/**
 * Retrieves data from {@link org.radargun.traits.InternalsExposition} and places them into timeline
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InternalsMonitor implements Monitor {
   private final InternalsExposition internalsExposition;
   private final Timeline timeline;

   public InternalsMonitor(InternalsExposition internalsExposition, Timeline timeline) {
      this.internalsExposition = internalsExposition;
      this.timeline = timeline;
   }

   @Override
   public void start() {
   }

   @Override
   public void stop() {
   }

   @Override
   public void run() {
      long now = TimeService.currentTimeMillis();
      for (Map.Entry<String, Number> entry : internalsExposition.getValues().entrySet()) {
         timeline.addValue(entry.getKey(), new Timeline.Value(now, entry.getValue()));
      }
   }

   @Override
   public boolean equals(Object o) {
      return o != null && o.getClass() == this.getClass();
   }

   @Override
   public int hashCode() {
      return super.hashCode();
   }
}

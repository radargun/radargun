import java.io.IOException;
import java.util.Arrays;

import org.radargun.config.Cluster;
import org.radargun.reporting.Timeline;
import org.radargun.reporting.html.TimelineDocument;
import org.radargun.utils.TimeService;
import org.testng.annotations.Test;

/**
 * Writes a basic {@link TimelineDocument} with few charts.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class TimelineChartsTest {

   @Test
   public void test() {
      long now = TimeService.currentTimeMillis();
      Timeline t0 = new Timeline(0);
      Timeline t1 = new Timeline(1);
      t0.addValue("Category A", new Timeline.Value(now + 1000, 10));
      t0.addValue("Category A", new Timeline.Value(now + 2000, 5));
      t0.addValue("Category A", new Timeline.Value(now + 3000, 8));
      t0.addValue("Category A", new Timeline.Value(now + 4000, 12));

      t1.addValue("Category A", new Timeline.Value(now + 1000, 13));
      t1.addValue("Category A", new Timeline.Value(now + 2000, 15));
      t1.addValue("Category A", new Timeline.Value(now + 4000, 5));

      t0.addEvent("Category B", new Timeline.TextEvent(now + 800, "Started"));
      t0.addEvent("Category B", new Timeline.IntervalEvent(now + 1500, "Stage one", 1000));
      t0.addEvent("Category B", new Timeline.IntervalEvent(now + 2600, "Stage two", 1500));

      t1.addEvent("Category B", new Timeline.TextEvent(now + 900, "Started"));
      t1.addEvent("Category B", new Timeline.IntervalEvent(now + 1400, "Stage one", 1100));
      t1.addEvent("Category B", new Timeline.IntervalEvent(now + 2700, "Stage two", 1600));

      t0.addValue("Category C", new Timeline.Value(now + 1000, 600));
      t0.addValue("Category C", new Timeline.Value(now + 2500, 800));
      t0.addValue("Category C", new Timeline.Value(now + 3100, 2000));
      t0.addValue("Category C", new Timeline.Value(now + 4000, 1200));

      t1.addValue("Category C", new Timeline.Value(now + 1200, 513));
      t1.addValue("Category C", new Timeline.Value(now + 2100, 912));
      t1.addValue("Category C", new Timeline.Value(now + 3800, 485));

      Cluster cluster = new Cluster();
      cluster.addGroup("default", 2);
      TimelineDocument doc = new TimelineDocument(new TimelineDocument.Configuration(), "test", "testconfig", "Test Config", Arrays.asList(t0, t1), cluster);
      try {
         doc.open();
         doc.writeTimelines();
         doc.close();
      } catch (IOException e) {
         e.printStackTrace();
      }
   }
}

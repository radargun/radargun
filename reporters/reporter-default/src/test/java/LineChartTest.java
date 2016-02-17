import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import org.radargun.reporting.html.LineChart;
import org.radargun.utils.Utils;
import org.testng.annotations.Test;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class LineChartTest {
   @Test
   public void testChartGeneration() {
      LineChart chart = new LineChart("foos", "bars");
      Random random = new Random();
      for (int category = 0; category < 3; category++) {
         double lastValue = 0, lastDev = 1;
         for (int i = 0; i < 30; ++i) {
            lastValue = lastValue + (random.nextBoolean() ? 1 : -1);
            lastDev = lastDev + random.nextDouble() - 0.3;
            chart.addValue(lastValue, lastDev, "Serie" + category, i, String.valueOf(Math.pow(2, i)));
         }
      }
      Path tempDirectory = null;
      try {
         tempDirectory = Files.createTempDirectory("LineChartTest_testChartGeneration");
         chart.setHeight(500).setWidth(600).save(new File(tempDirectory.toFile(), "foobar.png").toString());
      } catch (IOException e) {
         e.printStackTrace();
      } finally {
         if (tempDirectory != null) {
            Utils.deleteDirectory(tempDirectory.toFile());
         }
      }
   }

}

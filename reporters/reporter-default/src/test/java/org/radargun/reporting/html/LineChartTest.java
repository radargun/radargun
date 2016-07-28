package org.radargun.reporting.html;

import java.io.IOException;
import java.util.Random;

import org.testng.annotations.Test;

public class LineChartTest {
   @Test
   public void test() {
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
      try {
         chart.setHeight(500).setWidth(600).save("/tmp/foobar.png");
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

}

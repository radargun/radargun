package org.radargun.fwk;

import org.radargun.sysmonitor.AbstractActivityMonitor;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;

import static org.testng.Assert.assertEquals;

/**
 * @author mmarkus
 */
@Test (groups = "functional")
public class AbstractActivityMonitorTest {

   public void testAbstractActivityMonitor() {
      MyActivityMonitor ma = new MyActivityMonitor();
      ma.addMeasurement(1); //0
      ma.addMeasurement(2); //2
      ma.addMeasurement(3); //4
      ma.addMeasurement(4); //6
      ma.addMeasurement(5); //8
      LinkedHashMap<Integer,BigDecimal> map = ma.formatForGraph(2, 2);

      System.out.println("map = " + map);

      assertEquals(map.size(), 3);
      assertEquals(map.get(0), new BigDecimal(1.5));
      assertEquals(map.get(4), new BigDecimal(3.5));
      assertEquals(map.get(8), new BigDecimal(5));

      ma.addMeasurement(6);//10
      LinkedHashMap<Integer,BigDecimal> map2 = ma.formatForGraph(2, 2);

      System.out.println("map2 = " + map2);
      assertEquals(map2.size(), 3);
      assertEquals(map2.get(0), new BigDecimal(2));
      assertEquals(map2.get(5), new BigDecimal(4.5));
      assertEquals(map2.get(10), new BigDecimal(6));
   }


   private static class MyActivityMonitor extends AbstractActivityMonitor {

      @Override
      public void run() {
      }

      public void addMeasurement(int val) {
         measurements.add(new BigDecimal(val));
      }
   }
}

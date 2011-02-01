package org.radargun.fwk;

import org.radargun.config.DomConfigParser;
import org.radargun.config.FixedSizeBenchmarkConfig;
import org.radargun.config.MasterConfig;
import org.radargun.config.ScalingBenchmarkConfig;
import org.radargun.utils.TypedProperties;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.AssertJUnit.assertEquals;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test
public class DomConfigAttributesParsingTest {

   public void simpleTest() throws Exception {
      DomConfigParser parser = new DomConfigParser();
      MasterConfig masterConfig = parser.parseConfig("config-attributes-benchmark.xml");
      List<FixedSizeBenchmarkConfig> benchmarks = masterConfig.getBenchmarks();
      assertEquals(benchmarks.size(),2);

      ScalingBenchmarkConfig sc = (ScalingBenchmarkConfig) benchmarks.get(0);
      TypedProperties configAttributes = sc.getConfigAttributes();
      assertEquals(configAttributes.getProperty("a"), "va");
      assertEquals(configAttributes.getIntProperty("i", -1), 1);
   }

}

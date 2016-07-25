package org.radargun.config;

import org.radargun.utils.FuzzyEntrySize;
import org.testng.annotations.Test;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;

/**
 * @author mgencur
 */
@Test
public class FuzzyConverterTest {

   public void testCorrectPercentage() {
      final String valueDistribution = "11%: 10, 20%: 100, 40.5%: 1000, 20%: 10000, 8.5%: 100000";
      FuzzyEntrySize fuzzyEntrySize = new FuzzyEntrySize.FuzzyConverter().convert(valueDistribution, Integer.class);

      Map<Integer, BigDecimal> expected = new HashMap<>();
      expected.put(10, BigDecimal.valueOf(0.11));
      expected.put(100, BigDecimal.valueOf(0.31));
      expected.put(1000, BigDecimal.valueOf(0.715));
      expected.put(10000, BigDecimal.valueOf(0.915));
      expected.put(100000, BigDecimal.valueOf(1));

      assertEquals(fuzzyEntrySize.getProbabilityMap(), expected);
   }

   @Test(expectedExceptions = IllegalStateException.class)
   public void testPercentageBiggerThanOne() {
      final String valueDistribution = "11%: 10, 20%: 100, 40.5%: 1000, 20%: 10000, 10%: 100000";
      new FuzzyEntrySize.FuzzyConverter().convert(valueDistribution, Integer.class);
   }

   public void testWeights() {
      final String valueDistribution = "2: 10, 3: 100"; //2 to 3 ratio (= 40 to 60 %)
      FuzzyEntrySize fuzzyEntrySize = new FuzzyEntrySize.FuzzyConverter().convert(valueDistribution, Integer.class);
      Map<Integer, BigDecimal> expected = new HashMap<>();
      expected.put(10, BigDecimal.valueOf(0.4));
      expected.put(100, BigDecimal.valueOf(1));

      assertEquals(fuzzyEntrySize.getProbabilityMap(), expected);
   }

   public void testCombinedPercentageAndWeights() {
      //50% taken by percentages and the rest is split 2/3
      final String valueDistribution = "10%: 10, 20%: 100, 20%: 1000, 2: 10000, 3: 100000";
      FuzzyEntrySize fuzzyEntrySize = new FuzzyEntrySize.FuzzyConverter().convert(valueDistribution, Integer.class);
      Map<Integer, BigDecimal> expected = new HashMap<>();
      expected.put(10, BigDecimal.valueOf(0.1));
      expected.put(100, BigDecimal.valueOf(0.3));
      expected.put(1000, BigDecimal.valueOf(0.5));
      expected.put(10000, BigDecimal.valueOf(0.7));
      expected.put(100000, BigDecimal.valueOf(1));

      assertEquals(fuzzyEntrySize.getProbabilityMap(), expected);
   }
}

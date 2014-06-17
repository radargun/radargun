package org.radargun.query;

import java.util.Random;

import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.stages.cache.generators.ValueGenerator;
import org.radargun.utils.Utils;

/**
 * Generates {@link NumberObject}
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class NumberObjectGenerator implements ValueGenerator {
   @Property(doc = "Minimal value (inclusive) of generated integer part")
   private long intMin = Integer.MIN_VALUE;

   @Property(doc = "Maximal value (inclusive) of generated integer part")
   private long intMax = Integer.MAX_VALUE;

   @Property(doc = "Minimal value (inclusive) of generated double part")
   private double doubleMin = 0;

   @Property(doc = "Minimal value (inclusive) of generated double part")
   private double doubleMax = 1;


   @Override
   public void init(String param, ClassLoader classLoader) {
      PropertyHelper.setProperties(this, Utils.parseParams(param), false, false);
   }

   @Override
   public Object generateValue(Object key, int size, Random random) {
      int i =  intMax > intMin ? (int)(random.nextLong() % (intMax - intMin) + intMin) : 0;
      double d = doubleMax > doubleMin ? random.nextDouble() * (doubleMax - doubleMin) + doubleMin : 0d;
      return new NumberObject(i, d);
   }

   @Override
   public int sizeOf(Object value) {
      return 0;  // TODO: Customise this generated block
   }

   @Override
   public boolean checkValue(Object value, int expectedSize) {
      return false;  // TODO: Customise this generated block
   }
}

package org.radargun.stressors;

import java.util.List;
import java.util.Map;

/**
 * Warmup class to be used together with the {@link PutGetStressor}
 * @author Mircea.markus@gmail.com
 */
public class PutGetWarmupStressor extends PutGetStressor {

   @Override
   protected Map<String, String> processResults(List<PutGetStressor.Stressor> stressors) {
      return super.processResults(stressors);
   }
}

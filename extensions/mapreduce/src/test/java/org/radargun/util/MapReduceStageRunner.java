package org.radargun.util;

import java.util.Map;

/**
 * @author Matej Cimbora
 */
public class MapReduceStageRunner extends CoreStageRunner {

   public MapReduceStageRunner(int clusterSize) {
      super(clusterSize);
   }

   @Override
   protected Map<Class<?>, Object> getDefaultTraitMap() {
      return MapReduceTraitRepository.getAllTraits();
   }
}

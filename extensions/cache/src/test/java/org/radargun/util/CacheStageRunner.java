package org.radargun.util;

import java.util.Map;

/**
 * @author Matej Cimbora
 */
public class CacheStageRunner extends CoreStageRunner {

   public CacheStageRunner(int clusterSize) {
      super(clusterSize);
   }

   @Override
   protected Map<Class<?>, Object> getDefaultTraitMap() {
      return CacheTraitRepository.getAllTraits();
   }
}

package org.radargun.util;

import java.util.Map;

/**
 * @author Matej Cimbora
 */
public class QueryStageRunner extends CoreStageRunner {

   public QueryStageRunner(int clusterSize) {
      super(clusterSize);
   }

   @Override
   protected Map<Class<?>, Object> getDefaultTraitMap() {
      return QueryTraitRepository.getAllTraits();
   }
}

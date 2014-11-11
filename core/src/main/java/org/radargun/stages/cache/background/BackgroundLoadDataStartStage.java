package org.radargun.stages.cache.background;

import java.util.List;

import org.radargun.config.Stage;
import org.radargun.stages.cache.test.LoadDataStage;

/**
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
@Stage(doc = "Allows to load data into a cache in the background, while other stages may take place. To force process " +
      "termination, use BackgroundLoadDataStopStage.")
public class BackgroundLoadDataStartStage extends LoadDataStage {

   private static final String BACKGROUND_LOADERS = "BackgroundLoaders";

   @Override
   protected List<Thread> startLoaders() {
      List<Thread> newLoaders = super.startLoaders();
      List<Thread> previousLoaders = (List<Thread>) slaveState.get(BACKGROUND_LOADERS);
      if (previousLoaders == null) {
         slaveState.put(BACKGROUND_LOADERS, newLoaders);
      } else {
         previousLoaders.addAll(newLoaders);
      }
      return newLoaders;
   }

   @Override
   protected void stopLoaders(List<Thread> loaders) throws Exception {
      // do nothing
   }
}

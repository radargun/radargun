package org.radargun.stages;

import org.radargun.MainStage;
import org.radargun.config.Stage;
import org.radargun.state.MainState;

/**
 * Support class for MainStages.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(doc = "Parent class for stages performed on main.")
public abstract class AbstractMainStage extends AbstractStage implements MainStage {

   protected MainState mainState;

   public void init(MainState mainState) {
      this.mainState = mainState;
   }
}

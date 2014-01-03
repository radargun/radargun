package org.radargun.stages;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.MasterStage;
import org.radargun.config.Stage;
import org.radargun.state.MasterState;
import org.radargun.utils.ClassLoadHelper;

/**
 * Support class for MasterStages.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(doc = "")
public abstract class AbstractMasterStage extends AbstractStage implements MasterStage {

   private static final String PREV_PRODUCT = "AbstractMasterStage.previousProduct";
   private static final String CLASS_LOADER = "AbstractMasterStage.classLoader";
   protected Log log = LogFactory.getLog(getClass());
      
   protected MasterState masterState;
   private ClassLoadHelper classLoadHelper;

   public void init(MasterState masterState) {
      this.masterState = masterState;
      classLoadHelper = new ClassLoadHelper(true, this.getClass(),
            masterState.nameOfTheCurrentBenchmark(), masterState, PREV_PRODUCT, CLASS_LOADER);
   }

   public AbstractMasterStage clone() {
      return (AbstractMasterStage) super.clone();
   }

   protected Object createInstance(String classFqn) throws Exception {
      return classLoadHelper.createInstance(classFqn);
   }
}

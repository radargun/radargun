package org.radargun.stages;

import java.net.URLClassLoader;

import org.apache.log4j.Logger;
import org.radargun.state.MasterState;
import org.radargun.utils.Utils;
import org.radargun.MasterStage;

/**
 * Support class for MasterStages.
 *
 * @author Mircea.Markus@jboss.com
 */
public abstract class AbstractMasterStage implements MasterStage {

   private static final String PREV_PRODUCT = "AbstractMasterStage.previousProduct";
   private static final String CLASS_LOADER = "AbstractMasterStage.classLoader";
   protected Logger log = Logger.getLogger(getClass());
   
   protected MasterState masterState;
   private boolean useSmartClassLoading = true;

   public void init(MasterState masterState) {
      this.masterState = masterState;
   }

   public AbstractMasterStage clone() {
      try {
         return (AbstractMasterStage) super.clone();
      } catch (CloneNotSupportedException e) {
         throw new IllegalStateException(e);
      }
   }

   @Override
   public String toString() {
      return "{An instance of " + getClass().getSimpleName() + "}";
   }
   
   protected Object createInstance(String classFqn) throws Exception {
      if (!useSmartClassLoading) {
         return Class.forName(classFqn).newInstance();
      }
      URLClassLoader classLoader;
      String prevProduct = (String) masterState.get(PREV_PRODUCT);
      String currentProduct = masterState.nameOfTheCurrentBenchmark();
      if (prevProduct == null || !prevProduct.equals(currentProduct)) {
         classLoader = Utils.buildProductSpecificClassLoader(currentProduct, this.getClass().getClassLoader());
         masterState.put(CLASS_LOADER, classLoader);
         masterState.put(PREV_PRODUCT, currentProduct);
      } else {//same product and there is a class loader
         classLoader = (URLClassLoader) masterState.get(CLASS_LOADER);
      }
      log.debug("Creating newInstance " + classFqn + " with classloader " + classLoader);
      Thread.currentThread().setContextClassLoader(classLoader);
      return classLoader.loadClass(classFqn).newInstance();
   }

}

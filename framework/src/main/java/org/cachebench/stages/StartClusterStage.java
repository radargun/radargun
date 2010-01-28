package org.cachebench.stages;

import org.cachebench.CacheWrapper;
import org.cachebench.DistStageAck;
import org.cachebench.state.MasterState;
import org.cachebench.utils.Utils;

import java.net.URLClassLoader;

/**
 * Stage that strts a CacheWrapper on each slave.
 *
 * @author Mircea.Markus@jboss.com
 */
public class StartClusterStage extends AbstractDistStage {

   private String productName;
   private boolean useSmartClassLoading = true;

   private String config;
   private final int TRY_COUNT = 180;
   private static final String PREV_PRODUCT = "StartClusterStage.previousProduct";
   private static final String CLASS_LOADER = "StartClusterStage.classLoader";

   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      if (slaveState.getCacheWrapper() != null) {
         log.info("Wrapper already set on this slave, not starting it again.");
         return ack;
      }
      log.info("Ack master's StartCluster stage. Local address is: " + slaveState.getLocalAddress() + ". This slave's index is: " + getSlaveIndex());
      CacheWrapper wrapper;
      try {
         String plugin = Utils.getCacheWrapperFqnClass(productName);
         wrapper = (CacheWrapper) createInstance(plugin);
         wrapper.setUp(config, false);
         slaveState.setCacheWrapper(wrapper);
         for (int i = 0; i < TRY_COUNT; i++) {
            int numMembers = wrapper.getNumMembers();
            if (numMembers != getActiveSlaveCount()) {
               String msg = "Number of members=" + numMembers + " is not the one expected: " + getActiveSlaveCount();
               log.info(msg);
               Thread.sleep(1000);
               if (i == TRY_COUNT) {
                  ack.setError(true);
                  ack.setErrorMessage(msg);
                  return ack;
               }
            } else {
               log.info("Number of members is the one expected: " + wrapper.getNumMembers());
               break;
            }
         }
      } catch (Exception e) {
         log.error("Issues while instantiating/starting cache wrapper", e);
         ack.setError(true);
         ack.setRemoteException(e);
         return ack;
      }
      log.info("Successfully started cache wrapper on slave " + getSlaveIndex() + ": " + wrapper);
      return ack;
   }

   private Object createInstance(String classFqn) throws Exception {
      if (!useSmartClassLoading) {
         return Class.forName(classFqn).newInstance();
      }
      URLClassLoader classLoader;
      String prevProduct = (String) slaveState.get(PREV_PRODUCT);
      if (prevProduct == null || !prevProduct.equals(productName)) {
         classLoader = createLoader();
         slaveState.put(CLASS_LOADER, classLoader);
         slaveState.put(PREV_PRODUCT, productName);
      } else {//same product and there is a class loader
         classLoader = (URLClassLoader) slaveState.get(CLASS_LOADER);
      }
      Thread.currentThread().setContextClassLoader(classLoader);
      return classLoader.loadClass(classFqn).newInstance();
   }

   private URLClassLoader createLoader() throws Exception {
      return Utils.buildProductSpecificClassLoader(productName, this.getClass().getClassLoader());
   }

   public void setConfig(String config) {
      this.config = config;
   }

   public void setUseSmartClassLoading(boolean useSmartClassLoading) {
      this.useSmartClassLoading = useSmartClassLoading;
   }

   @Override
   public void initOnMaster(MasterState masterState, int slaveIndex) {
      super.initOnMaster(masterState, slaveIndex);
      this.productName = masterState.nameOfTheCurrentBenchmark();
   }

   @Override
   public String toString() {
      return "StartClusterStage {" +
            "productName='" + productName + '\'' +
            ", useSmartClassLoading=" + useSmartClassLoading +
            ", config=" + config +
            ", " + super.toString();
   }
}

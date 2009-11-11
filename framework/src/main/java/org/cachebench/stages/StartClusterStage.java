package org.cachebench.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;
import org.cachebench.DistStageAck;

import java.util.Map;

/**
 * Stage that strts a CacheWrapper on each slave.
 *
 * @author Mircea.Markus@jboss.com
 */
public class StartClusterStage extends AbstractDistStage {

   private static Log log = LogFactory.getLog(StartClusterStage.class);

   private String chacheWrapperClass;
   private Map<String, String> wrapperStartupParams;
   private final int TRY_COUNT = 10;

   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      if (slaveState.getCacheWrapper() != null) {
         log.info("Wrapper already set on this slave, not starting it again.");
         return ack;
      }
      log.info("Ack master's StartCluster stage. Local address is: " + slaveState.getLocalAddress() + ". This slave's index is: " + getSlaveIndex());
      CacheWrapper wrapper;
      try {
         wrapper = (CacheWrapper) createInstance(chacheWrapperClass);
         wrapper.init(wrapperStartupParams);
         wrapper.setUp();
         slaveState.setCacheWrapper(wrapper);
         for (int i = 0; i< TRY_COUNT; i++) {
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
               log.info("Number of members is the one expected.");
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
      return Class.forName(classFqn).newInstance();
   }

   public void setChacheWrapperClass(String chacheWrapperClass) {
      this.chacheWrapperClass = chacheWrapperClass;
   }

   public void setWrapperStartupParams(Map<String, String> wrapperStartupParams) {
      this.wrapperStartupParams = wrapperStartupParams;
   }

   @Override
   public String toString() {
      return "StartClusterStage{" +
            "chacheWrapperClass='" + chacheWrapperClass + '\'' +
            ", wrapperStartupParams=" + wrapperStartupParams +
            "} " + super.toString();
   }
}

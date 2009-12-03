package org.cachebench.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;
import org.cachebench.DistStageAck;
import org.cachebench.state.MasterState;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Stage that strts a CacheWrapper on each slave.
 *
 * @author Mircea.Markus@jboss.com
 */
public class StartClusterStage extends AbstractDistStage {

   private static Log log = LogFactory.getLog(StartClusterStage.class);
   private String productName;
   private boolean useSmartClassLoading = true;

   private Map<String, String> wrapperStartupParams;
   private final int TRY_COUNT = 180;
   private static final String PLUGINS_DIR = "plugins";

   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      if (slaveState.getCacheWrapper() != null) {
         log.info("Wrapper already set on this slave, not starting it again.");
         return ack;
      }
      log.info("Ack master's StartCluster stage. Local address is: " + slaveState.getLocalAddress() + ". This slave's index is: " + getSlaveIndex());
      CacheWrapper wrapper;
      try {
         String plugin = tryLoadFromFixedLocation();
         wrapper = (CacheWrapper) createInstance(plugin);
         wrapper.init(wrapperStartupParams);
         wrapper.setUp();
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

   private String tryLoadFromFixedLocation() {
      File file = new File(PLUGINS_DIR + File.separator + productName + File.separator + "conf" + File.separator + "cacheprovider.properties");
      if (!file.exists()) {
         log.warn("Could not find a plugin descriptor : " + file);
         return null;
      }
      Properties properties = new Properties();
      FileInputStream inStream = null;
      try {
         inStream = new FileInputStream(file);
         properties.load(inStream);
         return properties.getProperty("org.cachebenchfwk.wrapper");
      } catch (IOException e) {
         throw new RuntimeException(e);
      } finally {
         if (inStream != null)
            try {
               inStream.close();
            } catch (IOException e) {
               log.warn(e);
            }
      }
   }

   private Object createInstance(String classFqn) throws Exception {
      if (!useSmartClassLoading) {
         return Class.forName(classFqn).newInstance();
      }
      log.trace("Using smart class laoding");
      File libFolder = new File(PLUGINS_DIR + File.separator + productName + File.separator + "lib");
      if (!libFolder.isDirectory()) {
         String message = "Could not find lib directory: " + libFolder.getAbsolutePath();
         log.error(message);
         throw new IllegalStateException(message);
      }
      String[] jarsSrt = libFolder.list(new FilenameFilter() {
         public boolean accept(File dir, String name) {
            String fileName = name.toUpperCase();
            if (fileName.endsWith("JAR") || fileName.toUpperCase().endsWith("ZIP")) {
               if (log.isTraceEnabled()) {
                  log.trace("Accepting file: " + fileName);
               }
               return true;
            } else {
               if (log.isTraceEnabled()) {
                  log.trace("Rejecting file: " + fileName);
               }
               return false;
            }
         }
      });
      List<URL> jars = new ArrayList<URL>();
      for (String file : jarsSrt) {
         File aJar = new File(libFolder, file);
         if (!aJar.exists() || !aJar.isFile()) {
            throw new IllegalStateException();
         }
         jars.add(aJar.toURI().toURL());
      }
      File confDir = new File(PLUGINS_DIR + File.separator + productName + File.separator + "conf/");
      jars.add(confDir.toURI().toURL());
      URLClassLoader classLoader = new URLClassLoader(jars.toArray(new URL[jars.size()]), this.getClass().getClassLoader());
      Thread.currentThread().setContextClassLoader(classLoader);
      return classLoader.loadClass(classFqn).newInstance();
   }

   public void setWrapperStartupParams(Map<String, String> wrapperStartupParams) {
      this.wrapperStartupParams = wrapperStartupParams;
   }

   public void setUseSmartClassLoading(boolean useSmartClassLoading) {
      this.useSmartClassLoading = useSmartClassLoading;
   }

   @Override
   public void initOnMaster(MasterState masterState, int totalSlavesCount) {
      super.initOnMaster(masterState, totalSlavesCount);
      this.productName = masterState.nameOfTheCurrentBenchmark();
   }

   @Override
   public String toString() {
      return "StartClusterStage{" +
            ", wrapperStartupParams=" + wrapperStartupParams +
            "} " + super.toString();
   }
}

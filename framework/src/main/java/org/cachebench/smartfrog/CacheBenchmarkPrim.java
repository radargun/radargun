package org.cachebench.smartfrog;

//import org.smartfrog.sfcore.common.SmartFrogException;
//import org.smartfrog.sfcore.common.SmartFrogResolutionException;
//import org.smartfrog.sfcore.common.SmartFrogLivenessException;
//import org.smartfrog.sfcore.logging.LogSF;
//import org.smartfrog.sfcore.prim.Prim;
//import org.smartfrog.sfcore.prim.PrimImpl;
//import org.smartfrog.sfcore.prim.TerminationRecord;

import java.io.*;
import java.rmi.RemoteException;

/**
 * @author Mircea.Markus@jboss.com
 */
public class CacheBenchmarkPrim// extends PrimImpl implements Prim
{
//   private static LogSF log;
//
//   private int nodeIndex = -1;
//   private String cacheDistribution;
//   private File toRunIn;
//   private int clusterSize = -1;
//   private String scriptToExec;
//   private int minClusterSize;
//   private int maxClusterSize;
//
//   public CacheBenchmarkPrim() throws RemoteException
//   {
//   }
//
//   public void sfPing(Object o) throws SmartFrogLivenessException, RemoteException
//   {
//      super.sfPing(o);    //To change body of overridden methods use File | Settings | File Templates.
//   }
//
//   public synchronized void sfDeploy() throws SmartFrogException, RemoteException
//   {
//      super.sfDeploy();
//      log = super.sfGetApplicationLog();
//      log.trace("deploy started");
//      //reading the attributes here
//      nodeIndex = (Integer) sfResolve("nodeIndex");
//      log.info("Received current index: " + nodeIndex);
//      cacheDistribution = (String) sfResolve("cacheDistribution");
//      log.info("Received cache distribution: " + cacheDistribution);
//      toRunIn = getFwkHomeDir();
//      log.info("Received homedir: " + toRunIn);
//      clusterSize = (Integer)sfResolve("clusterSize");
//      log.info("Received cluster size: " + clusterSize);
//      minClusterSize = (Integer) sfResolve("minClusterSize");
//      log.info("Received MIN cluster size: " + minClusterSize);
//      maxClusterSize = (Integer) sfResolve("maxClusterSize");
//      log.info("Received MAX cluster size: " + maxClusterSize);
//      scriptToExec = (String) sfResolve("scriptToExec");
//      log.info("Received stringToExec: " + scriptToExec);
//      log.trace("Deploy finished");
//   }
//
//   public synchronized void sfStart() throws SmartFrogException, RemoteException
//   {
//      if ((nodeIndex + 1) < clusterSize)
//      {
//         log.info("Running in a separate thread, not last node in the compound" + getNodeDescription());
//         Thread thread = new Thread()
//         {
//            public void run()
//            {
//               runBenchmark();
//            }
//         };
//         thread.start();
//      }
//      //the last node should run in a sync manner so that the coumpound would only end when the test ends
//      else
//      {
//         log.info("Running in the same thread as this is the last node in the compound " + getNodeDescription());
//         runBenchmark();
//      }
//   }
//
//   private void runBenchmark()
//   {
//      try
//      {
//         super.sfStart();
//         log.trace("Entered sfStart...");
//         if (clusterSize <= nodeIndex)
//         {
//            log.info("Not processing this node as compund is smaller than " + nodeIndex + getNodeDescription());
//            return;
//         }
//         if (clusterSize < minClusterSize || clusterSize > maxClusterSize)
//         {
//            log.info("Only clusters with size in range (" + minClusterSize + ", " + maxClusterSize + ") are " +
//                  "processed, skiping this one" + getNodeDescription());
//            correctTerminationOfTestsIfNeeded();
//            return;
//         }
//         String command = scriptToExec + " " + nodeIndex + " " + cacheDistribution + " " + clusterSize;
//         log.info("Executing command: " + command);
//         Process process = Runtime.getRuntime().exec(command, null, toRunIn);
//         InputStreamReader reader = new InputStreamReader(process.getInputStream());
//         BufferedReader bufferedReader = new LineNumberReader(reader);
//         String line;
//         while ((line = bufferedReader.readLine()) != null)
//         {
//            log.info(scriptToExec + " >>> " + line);
//         }
//         bufferedReader.close();
//         int exitValue = process.waitFor();
//         if (exitValue != 0 )
//         {
//             log.warn("Script exited with code: " + exitValue);
//         }
//
//         correctTerminationOfTestsIfNeeded();
//      } catch (SmartFrogException e)
//      {
//         log.error("Unexpected error:" + e.getMessage(), e);
//         terminate(e);
//      } catch (IOException e)
//      {
//         log.warn("Does the script have X rights?", e);
//         terminate(e);
//
//      } catch (InterruptedException e)
//      {
//         log.err("This is quite strange", e);
//         terminate(e);
//      }
//   }
//
//   /**
//    * The test will only be terminated if this is the last cluster compound running.
//    */
//   private void correctTerminationOfTestsIfNeeded()
//   {
//      if (this.clusterSize == this.maxClusterSize)
//      {
//         log.info("Good news, terminating ALL the tests, terminator is " + getNodeDescription());
//         TerminationRecord terminationRecord = new TerminationRecord(TerminationRecord.NORMAL, "terminated the benchmark " +
//               getNodeDescription(), null);
//         sfTerminate(terminationRecord);
//         log.info("Test terminated successfully " + getNodeDescription());
//      }
//   }
//
//   private void terminate(Exception e)
//   {
//      TerminationRecord terminationRecord = new TerminationRecord(TerminationRecord.ABNORMAL , "terminated the benchmark " +
//            getNodeDescription(),null, e);
//      sfTerminate(terminationRecord);
//
//   }
//
//   private File getFwkHomeDir()
//         throws SmartFrogResolutionException, RemoteException
//   {
//      File toRunIn;
//      String cacheBenchmarkHome = sfResolve("cacheBenchmarkHome") + "";
//      toRunIn = new File(cacheBenchmarkHome);
//      if (!toRunIn.isDirectory())
//      {
//         log.error("cacheBenchmarkHome is not specified correctly: " + cacheBenchmarkHome);
//      }
//      return toRunIn;
//   }
//
//   protected synchronized void sfTerminateWith(TerminationRecord terminationRecord)
//   {
//      super.sfTerminateWith(terminationRecord);
//      log.info("sfTerminateWith called with value:" +  terminationRecord);
//   }
//
//   public String getNodeDescription()
//   {
//      return "( clusterSize:" + clusterSize + ", nodeIndex:" + this.nodeIndex + " )";
//   }
//
//   /**
//    * Ugly HACK - seems like if the parent is pending in an sfRun, e.g. then it would not send hart beat checks
//    * to the childrens so they would consider a issues.
//    * Might be that this is not a totally proper scenarion for using the framework, but it solves our needs.
//    */
//   protected void sfLivenessFailure(Object source, Object target, Throwable failure)
//   {
//      log.trace("Recieved liveness error, ignoring. Source:" + source + ", target:" + target + ", failure: " + failure);
//   }
}

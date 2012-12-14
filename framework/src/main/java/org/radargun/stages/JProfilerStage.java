package org.radargun.stages;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.StringTokenizer;

/**
 * Stage for invoking operations on JProfiler.
 * Remember to set up JVM args: "-agentpath:/path/to/libjprofilerti.so=offline,id=100,config=/path/to/configuration.xml"
 *
 * @author rvansa
 * @since 4.0
 */
@Stage(doc = "Stage for invoking operations on JProfiler.\nRemember to set up JVM args: " +
      "\"-agentpath:/path/to/libjprofilerti.so=offline,id=100,config=/path/to/configuration.xml\"")
public class JProfilerStage extends AbstractDistStage {

   public static final String CONTROLLER_OBJECT_NAME = "com.jprofiler.api.agent.mbean:type=Controller";

   public enum Operation {
      NO_OPERATION(null, null),
      START_METHOD_STATS_RECORDING("startMethodStatsRecording", new String[] {}),
      STOP_METHOD_STATS_RECORDING("startMethodStatsRecording", new String[] {}),
      START_CPU_RECORDING("startCPURecording", new String[] { boolean.class.getName() }),
      STOP_CPU_RECORDING("stopCPURecording", new String[] {}),
      SAVE_SNAPSHOT("saveSnapshot", new String[] { String.class.getName() });

      private String name;
      private String[] signature;

      private Operation(String name, String[] signature) {
         this.name = name;
         this.signature = signature;
      }

      public String getName() {
         return name;
      }

      public String[] getSignature() {
         return signature;
      }
   }

   @Property(optional = false, doc = "Operations that should be invoked on the Controller")
   protected Collection<Operation> operations;

   @Property(doc = "Path where the snapshot should be written (for SAVE_SNAPSHOT).")
   protected String snapshotOutput;

   @Property(doc = "If true, any previously accumulated CPU profiling data will be discarded. If false, CPU data will" +
         "be accumulated across pairs of invocations of START_CPU_RECORDING and STOP_CPU_RECORDING. Default is false.")
   protected boolean resetCPUStats;

   @Override
   public DistStageAck executeOnSlave() {
      if (operations == null || operations.isEmpty()) {
         log.warn("No operation specified");
         return newDefaultStageAck();
      }
      for (Operation operation : operations) {
         MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
         try {
            ObjectName objectName = new ObjectName(CONTROLLER_OBJECT_NAME);
            mbs.invoke(objectName, operation.getName(), getParams(operation), operation.getSignature());
         } catch (InstanceNotFoundException e) {
            return error(e);
         } catch (MalformedObjectNameException e) {
            return error(e);
         } catch (ReflectionException e) {
            return error(e);
         } catch (MBeanException e) {
            return error(e);
         }
      }
      return newDefaultStageAck();
   }

   private Object[] getParams(Operation operation) {
      switch (operation) {
         case NO_OPERATION:
            return null;
         case STOP_METHOD_STATS_RECORDING:
         case START_METHOD_STATS_RECORDING:
         case STOP_CPU_RECORDING:
            return new Object[0];
         case SAVE_SNAPSHOT:
            if (snapshotOutput == null) throw new NullPointerException("snapshotOutput not set");
            return new Object[] { snapshotOutput };
         case START_CPU_RECORDING:
            return new Object[] { resetCPUStats };
         default:
            throw new UnsupportedOperationException();
      }
   }

   private DistStageAck error(Exception e) {
      String message = "Failed to execute JMX operations";
      log.error(message, e);
      DefaultDistStageAck ack = newDefaultStageAck();
      ack.setError(true);
      ack.setErrorMessage(message);
      ack.setRemoteException(e);
      return ack;
   }
}

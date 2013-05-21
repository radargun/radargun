/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.radargun.stages;

import java.lang.management.ManagementFactory;
import java.util.Collection;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;

/**
 * Stage for invoking operations on JProfiler. Remember to set up JVM args:
 * "-agentpath:/path/to/libjprofilerti.so=offline,id=100,config=/path/to/configuration.xml"
 * 
 * @author rvansa
 * @since 4.0
 */
@Stage(doc = "Stage for invoking operations on JProfiler.\nRemember to set up JVM args: "
      + "\"-agentpath:/path/to/libjprofilerti.so=offline,id=100,config=/path/to/configuration.xml\"")
public class JProfilerStage extends AbstractDistStage {

   public static final String JPROFILER6_CONTROLLER_OBJECT_NAME = "com.jprofiler.api.agent.mbean:type=Controller";
   public static final String JPROFILER7_CONTROLLER_OBJECT_NAME = "com.jprofiler.api.agent.mbean:type=RemoteController";
   private ObjectName objectName;

   public enum Operation {
      NO_OPERATION(null, null), 
      START_METHOD_STATS_RECORDING("startMethodStatsRecording", new String[] {}), 
      STOP_METHOD_STATS_RECORDING("stopMethodStatsRecording", new String[] {}), 
      START_CPU_RECORDING("startCPURecording", new String[] { boolean.class.getName() }), 
      STOP_CPU_RECORDING("stopCPURecording", new String[] {}), 
      SAVE_SNAPSHOT("saveSnapshot", new String[] { String.class.getName() }), 
      START_MEMORY_RECORDING("startAllocRecording", new String[] { boolean.class.getName() }), 
      STOP_MEMORY_RECORDING("stopAllocRecording", new String[] {}), 
      START_MONITOR_RECORDING("startMonitorRecording", new String[] {}), 
      STOP_MONITOR_RECORDING("stopMonitorRecording", new String[] {}), 
      START_THREAD_RECORDING("startThreadProfiling", new String[] {}), 
      STOP_THREAD_RECORDING("stopThreadProfiling", new String[] {}), 
      START_VM_TELEMETRY_RECORDING("startVMTelemetryRecording", new String[] {}), 
      STOP_VM_TELEMETRY_RECORDING("stopVMTelemetryRecording", new String[] {});

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

   @Property(doc = "Directory where the snapshot should be written (for SAVE_SNAPSHOT).")
   protected String snapshotDirectory;

   @Property(doc = "If true, any previously accumulated CPU profiling data will be discarded. If false, CPU data will"
         + "be accumulated across pairs of invocations of START_CPU_RECORDING and STOP_CPU_RECORDING. Default is false.")
   protected boolean resetCPUStats;

   @Property(doc = "If true, any previously accumulated Memory profiling data will be discarded. If false, CPU data will"
         + "be accumulated across pairs of invocations of START_MEMORY_RECORDING and STOP_MEMORY_RECORDING. Default is false.")
   protected boolean resetMemoryStats;

   @Override
   public DistStageAck executeOnSlave() {
      if (operations == null || operations.isEmpty()) {
         log.warn("No operation specified");
         return newDefaultStageAck();
      }
      for (Operation operation : operations) {
         MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
         try {
            objectName = new ObjectName(JPROFILER7_CONTROLLER_OBJECT_NAME);
            if (!mbs.isRegistered(objectName)) {
               objectName = new ObjectName(JPROFILER6_CONTROLLER_OBJECT_NAME);
               if (!mbs.isRegistered(objectName)) {
                  log.info("JProfiler not enabled on this node.");
                  return newDefaultStageAck();
               }
            }
            mbs.invoke(objectName, operation.getName(), getParams(operation), operation.getSignature());
         } catch (Exception e) {
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
      case STOP_MEMORY_RECORDING:
         return new Object[0];
      case SAVE_SNAPSHOT:
         if (snapshotDirectory == null)
            throw new NullPointerException("snapshotOutput not set");
         return new Object[] { snapshotDirectory + this.productName + "-" + this.configName + "-node" + this.slaveIndex
               + "-profiler.jps" };
      case START_CPU_RECORDING:
         return new Object[] { resetCPUStats };
      case START_MEMORY_RECORDING:
         return new Object[] { resetMemoryStats };
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

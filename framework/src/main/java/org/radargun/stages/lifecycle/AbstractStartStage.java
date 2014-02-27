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
package org.radargun.stages.lifecycle;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.CsvReportGenerationStage;
import org.radargun.stages.DefaultDistStageAck;
import org.radargun.stages.helpers.StartHelper;
import org.radargun.stages.helpers.StartStopTime;

/**
 * Common base for stages that start slaves.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "")
public abstract class AbstractStartStage extends AbstractDistStage {

   public static final String PROP_FILE = "file";
   public static final String PROP_CONFIG_NAME = "configName";
   public static final String PROP_PLUGIN = "plugin";

   @Property(doc = "Set of slaves where the start may fail but this will not cause an error. Default is none.")
   protected Collection<Integer> mayFailOn;

   @Property(readonly = true, doc = "Configuration file used for this stage")
   protected String configFile;
   protected String service;
   protected Map<String, String> configProperties;

   public void setup(String service, String configFile, Map<String, String> configProperties) {
      this.service = service;
      this.configFile = configFile;
      this.configProperties = new HashMap<String, String>(configProperties);
      this.configProperties.put(PROP_CONFIG_NAME, slaveState.getConfigName());
      this.configProperties.put(PROP_PLUGIN, slaveState.getPlugin());
      this.configProperties.put(PROP_FILE, configFile);
   }

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks) {
      boolean success = true;
      logDurationInfo(acks);
      for (DistStageAck stageAck : acks) {
         DefaultDistStageAck defaultStageAck = (DefaultDistStageAck) stageAck;
         if (defaultStageAck.isError() && (mayFailOn == null || !mayFailOn.contains(stageAck.getSlaveIndex()))) {
            log.warn("Received error ack " + defaultStageAck);
            return false;
         } else if (defaultStageAck.isError()) {
            log.info("Received allowed error ack " + defaultStageAck);
         } else {
            log.trace("Received success ack " + defaultStageAck);
            StartStopTime times = ((StartStopTime) defaultStageAck.getPayload());
            if (times != null && times.getStartTime() >= 0) {
               CsvReportGenerationStage.addResult(masterState, stageAck.getSlaveIndex(), StartHelper.START_TIME,
                     times.getStartTime());
            }
         }
      }
      if (log.isTraceEnabled())
         log.trace("All ack messages were successful");
      return success;
   }
}

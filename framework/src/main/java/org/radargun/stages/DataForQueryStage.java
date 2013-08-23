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

import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.config.Stage;
import org.radargun.state.SlaveState;
import org.radargun.stressors.DataForQueryStressor;

import java.util.Map;

/**
 * Stage for inserting data into indexed cache for processing.
 *
 * @author Anna Manukyan
 */
@Stage(doc = "Stage which executes puts/gets indexed entries against index enabled cache.")
public class DataForQueryStage extends StressTestStage {

   @Property(doc = "The length of the generated indexed entry. Default is 100.")
   private int propertyLength = 100;

   @Property(doc = "Specifies whether the key generation should be done according to wildcard logic or no. Default is false.")
   private boolean isWildCard = false;

   @Property(doc = "Specifies the full path of the property file which contains different words for querying. No default value is provided. This property is mandatory.")
   private String dataPath = null;

   private static DataForQueryStressor stressTestStressor = null;

   public void initOnSlave(SlaveState slaveState) {
      super.initOnSlave(slaveState);
   }

   protected Map<String, Object> doWork() {
      log.info("Starting "+getClass().getSimpleName()+": " + this);

      stressTestStressor = new DataForQueryStressor(slaveState);

      stressTestStressor.setNodeIndex(getSlaveIndex(), getActiveSlaveCount());
      stressTestStressor.setDurationMillis(duration);
      PropertyHelper.copyProperties(this, stressTestStressor);

      Map<String, Object> result = stressTestStressor.stress(cacheWrapper);

      stressTestStressor.destroy();

      return result;
   }
}

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
import org.radargun.stressors.QueryStressor;

import java.util.Map;

/**
 * Executes Queries using Infinispan-Query API against the cache.
 *
 * @author Anna Manukyan
 */
@Stage(doc = "Stage which executes a Query using Infinispan-query API against all keys in the cache.")
public class QueryStage extends StressTestStage {

   @Property(optional = false, doc = "Boolean variable which shows whether the keyword query should be done or wildcard.")
   private boolean isWildcardQuery;

   @Property(optional = false, doc = "The name of the field for which the query should be executed.")
   private String onField;

   public void initOnSlave(SlaveState slaveState) {
      super.initOnSlave(slaveState);
   }

   protected Map<String, Object> doWork() {
      log.info("Starting "+getClass().getSimpleName()+": " + this);

      QueryStressor stressTestStressor = new QueryStressor(slaveState);
      stressTestStressor.setNodeIndex(getSlaveIndex(), getActiveSlaveCount());
      stressTestStressor.setDurationMillis(duration);

      PropertyHelper.copyProperties(this, stressTestStressor);

      return stressTestStressor.stress(cacheWrapper);
   }
}

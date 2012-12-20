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

import java.util.Map;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.features.MapReduceCapable;
import org.radargun.utils.Utils;

/**
 * Executes a MapReduce Task against the cache.
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Stage(doc = "Stage which executes a MapReduce Task against all keys in the cache.")
public class MapReduceStage extends AbstractDistStage {

   @Property(optional = false, doc = "Fully qualified class name of the "
         + "org.infinispan.distexec.mapreduce.Mapper implementation to execute.")
   private String mapperFqn;

   @Property(optional = false, doc = "Fully qualified class name of the "
         + "org.infinispan.distexec.mapreduce.Reducer implementation to execute.")
   private String reducerFqn;

   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck result = newDefaultStageAck();
      CacheWrapper cacheWrapper = slaveState.getCacheWrapper();
      Map<Object, Object> payload = null;

      if (cacheWrapper == null) {
         result.setErrorMessage("Not running test on this slave as the wrapper hasn't been configured.");
      } else {
         if (getSlaveIndex() == 0) {
            if (cacheWrapper instanceof MapReduceCapable) {
               if (mapperFqn != null && reducerFqn != null) {
                  long start = System.currentTimeMillis();
                  payload = ((MapReduceCapable) cacheWrapper).executeMapReduceTask(classLoadHelper, mapperFqn,
                        reducerFqn);
                  log.info("MapReduce task completed in " + Utils.prettyPrintMillis(System.currentTimeMillis() - start));
                  log.info("Result map contains '" + payload.keySet().size() + "' keys.");
                  result.setPayload(payload);
               } else {
                  result.setError(true);
                  result.setErrorMessage("Both the mapper and reducer class must be specified.");
               }
            } else {
               result.setError(true);
               result.setErrorMessage("MapReduce tasks are not supported by this cache");
            }
         }

      }
      return result;
   }

   public String getMapperFqn() {
      return mapperFqn;
   }

   public void setMapperFqn(String mapperFqn) {
      this.mapperFqn = mapperFqn;
   }

   public String getReducerFqn() {
      return reducerFqn;
   }

   public void setReducerFqn(String reducerFqn) {
      this.reducerFqn = reducerFqn;
   }

}

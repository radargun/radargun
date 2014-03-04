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
package org.radargun.service;

import org.infinispan.Cache;
import org.infinispan.distexec.mapreduce.MapReduceTask;
import org.infinispan.distexec.mapreduce.Reducer;
import org.radargun.utils.ClassLoadHelper;
import org.radargun.utils.Utils;

public class Infinispan52MapReduce<KIn, VIn, KOut, VOut, R> extends InfinispanMapReduce<KIn, VIn, KOut, VOut, R> {

   public Infinispan52MapReduce(Infinispan52EmbeddedService service) {
      super(service);
   }

   @Override
   public boolean setDistributeReducePhase(boolean distributeReducePhase) {
      this.distributeReducePhase = distributeReducePhase;
      return true;
   }

   @Override
   public boolean setUseIntermediateSharedCache(boolean useIntermediateSharedCache) {
      this.useIntermediateSharedCache = useIntermediateSharedCache;
      return true;
   }

   @Override
   protected MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTaskFactory() {
      Cache<KIn, VIn> cache = (Cache<KIn, VIn>) service.getCache(null);
      return new MapReduceTask<KIn, VIn, KOut, VOut>(cache, this.distributeReducePhase, this.useIntermediateSharedCache);
   }

   @Override
   public boolean setCombiner(String combinerFqn) {
      this.combinerFqn = combinerFqn;
      return true;
   }

   @Override
   protected MapReduceTask<KIn, VIn, KOut, VOut> setCombiner(MapReduceTask<KIn, VIn, KOut, VOut> task,
         ClassLoadHelper classLoadHelper, String combinerFqn) {
      if (combinerFqn != null) {
         try {
            @SuppressWarnings("unchecked")
            Reducer<KOut, VOut> combiner = (Reducer<KOut, VOut>) classLoadHelper.createInstance(combinerFqn);
            Utils.invokeMethodWithString(combiner, this.combinerParameters);
            task = task.combinedWith(combiner);
         } catch (Exception e) {
            throw (new IllegalArgumentException("Could not instantiate Combiner class: " + combinerFqn, e));
         }
      }
      return task;
   }

}

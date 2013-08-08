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
package org.radargun.cachewrappers;

import org.infinispan.Cache;
import org.infinispan.distexec.mapreduce.MapReduceTask;
import org.infinispan.distribution.ch.ConsistentHash;

public class Infinispan52MapReduceWrapper<KIn, VIn, KOut, VOut, R> extends
      InfinispanMapReduceWrapper<KIn, VIn, KOut, VOut, R> {

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
      Cache<KIn, VIn> cache = cacheManager.getCache(getCacheName());
      return new MapReduceTask<KIn, VIn, KOut, VOut>(cache, this.distributeReducePhase, this.useIntermediateSharedCache);
   }

   @Override
   protected int membersCount(ConsistentHash consistentHash) {
      return consistentHash.getMembers().size();
   }

   @Override
   public int getValueByteOverhead() {
      return 152;
   }   

}

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
package org.radargun.features;

import java.util.Map;

import org.radargun.CacheWrapper;
import org.radargun.utils.ClassLoadHelper;

public interface MapReduceCapable<KOut, VOut, R> extends CacheWrapper {

   /**
    * 
    * This method executes a MapReduce task against all of the keys in the cache using the specified
    * Mapper and Reducer classes.
    * 
    * @param classLoadHelper
    *           a <code>ClassLoadHelper</code> used to instantiate the classes
    * 
    * @param mapperFqn
    *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Mapper
    *           implementation. The implementation must have a no argument constructor.
    * 
    * @param reducerFqn
    *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Reducer
    *           implementation. The implementation must have a no argument constructor.
    * 
    * @param collatorFqn
    *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Collator
    *           implementation. The implementation must have a no argument constructor.
    * 
    * @return the collated result
    */
   public R executeMapReduceTask(ClassLoadHelper classLoadHelper, String mapperFqn, String reducerFqn,
         String collatorFqn);

   /**
    * 
    * This method executes a MapReduce task against all of the keys in the cache using the specified
    * Mapper and Reducer classes.
    * 
    * @param classLoadHelper
    *           a <code>ClassLoadHelper</code> used to instantiate the classes
    * 
    * @param mapperFqn
    *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Mapper
    *           implementation. The implementation must have a no argument constructor.
    * 
    * @param reducerFqn
    *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Reducer
    *           implementation. The implementation must have a no argument constructor.
    * 
    * @return a Map where each key is an output key and value is reduced value for that output key
    */
   public Map<KOut, VOut> executeMapReduceTask(ClassLoadHelper classLoadHelper, String mapperFqn, String reducerFqn);

   /**
    * 
    * This boolean determines if the Reduce phase of the MapReduceTask is distributed
    * 
    * @param distributedReduce
    *           if true this task will use distributed reduce phase execution
    * @return <code>true</code> if the CacheWrapper supports this flag, else <code>false</code>
    */
   public boolean setDistributeReducePhase(boolean distributeReducePhase);

   /**
    * 
    * This boolean determines if intermediate results of the MapReduceTask are shared
    * 
    * @param useIntermediateSharedCache
    *           if true this tasks will share intermediate value cache with other executing
    *           MapReduceTasks on the grid. Otherwise, if false, this task will use its own
    *           dedicated cache for intermediate values
    * @return <code>true</code> if the CacheWrapper supports this flag, else <code>false</code>
    */
   public boolean setUseIntermediateSharedCache(boolean useIntermediateSharedCache);

   /**
    * 
    * This method allows the caller to provide parameters to the Mapper, Reducer, and Collator
    * objects used in a MapReduce job. Each Map contains keys for each public method name, and values
    * for each single String parameter for the method. If no parameters are needed, these can be
    * set to an empty Map.
    * 
    * @param mapperParameters parameters for the Mapper object
    * @param reducerParameters parameters for the Reducer object
    * @param collatorParameters parameters for the Collator object
    */
   public void setParameters(Map<String, String> mapperParameters, Map<String, String> reducerParameters,
         Map<String, String> collatorParameters);

}

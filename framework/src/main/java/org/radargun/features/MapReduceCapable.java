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
import java.util.concurrent.TimeUnit;

import org.radargun.utils.ClassLoadHelper;

public interface MapReduceCapable<KOut, VOut, R> {

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
    * 
    * @throws an
    *            exception if an error occurs while executing the task
    */
   public R executeMapReduceTask(ClassLoadHelper classLoadHelper, String mapperFqn, String reducerFqn,
         String collatorFqn) throws Exception;

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
    * 
    * @throws an
    *            exception if an error occurs while executing the task
    */
   public Map<KOut, VOut> executeMapReduceTask(ClassLoadHelper classLoadHelper, String mapperFqn, String reducerFqn)
         throws Exception;

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
    * Set a timeout for the communication between the nodes during a Map Reduce task. Setting this
    * value to zero or less than zero means to wait forever.
    * 
    * @param timeout
    *           the value of the timeout
    * @param unit
    *           the unit of the timeout value
    * @return <code>true</code> if the CacheWrapper supports setting the timeout, else
    *         <code>false</code>
    */
   public boolean setTimeout(long timeout, TimeUnit unit);

   /**
    * 
    * This method allows the caller to provide parameters to the Mapper, Reducer, Combiner, and
    * Collator objects used in a MapReduce job. Each Map contains keys for each public method name,
    * and values for each single String parameter for the method. If no parameters are needed, these
    * can be set to an empty Map.
    * 
    * @param mapperParameters
    *           parameters for the Mapper object
    * @param reducerParameters
    *           parameters for the Reducer object
    * @param combinerParameters
    *           parameters for the Reducer object used as a combiner
    * @param collatorParameters
    *           parameters for the Collator object
    */
   public void setParameters(Map<String, String> mapperParameters, Map<String, String> reducerParameters,
         Map<String, String> combinerParameters, Map<String, String> collatorParameters);

   /**
    * 
    * Specifies a Reducer class to be used with the MapReduceTask during the combine phase
    * 
    * @param combinerFqn
    *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Reducer
    *           implementation. The implementation must have a no argument constructor.
    * @return <code>true</code> if the CacheWrapper supports this flag, else <code>false</code>
    */
   public boolean setCombiner(String combinerFqn);

}

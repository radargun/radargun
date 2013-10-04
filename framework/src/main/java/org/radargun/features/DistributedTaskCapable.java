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

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.radargun.utils.ClassLoadHelper;

public interface DistributedTaskCapable<T> {

   /**
    * 
    * This method executes the specified Distributed task against all of the keys in the cache.
    * 
    * @param classLoadHelper
    *           A <code>ClassLoadHelper</code> used to instantiate the classes
    * 
    * @param distributedCallableFqn
    *           The fully qualified class name for the org.infinispan.distexec.DistributedCallable
    *           or java.util.concurrent.Callable implementation. The implementation must have a no
    *           argument constructor. This class name is required.
    * 
    * @param executionPolicyName
    *           The name of one of the org.infinispan.distexec.DistributedTaskExecutionPolicy enums
    *           or <code>null</code>
    * 
    * @param failoverPolicyFqn
    *           The fully qualified class name for a
    *           org.infinispan.distexec.DistributedTaskFailoverPolicy implementation or
    *           <code>null</code>
    * 
    * @param nodeAddress
    * 
    * @param params
    * 
    * @return
    */
   public List<Future<T>> executeDistributedTask(ClassLoadHelper classLoadHelper, String distributedCallableFqn,
         String executionPolicyName, String failoverPolicyFqn, String nodeAddress, Map<String, String> params);
}

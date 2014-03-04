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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.infinispan.Cache;
import org.infinispan.distexec.DefaultExecutorService;
import org.infinispan.distexec.DistributedExecutorService;
import org.infinispan.distexec.DistributedTaskBuilder;
import org.infinispan.distexec.DistributedTaskExecutionPolicy;
import org.infinispan.distexec.DistributedTaskFailoverPolicy;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.remoting.transport.Address;
import org.infinispan.remoting.transport.Transport;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.DistributedTaskExecutor;
import org.radargun.utils.ClassLoadHelper;
import org.radargun.utils.Utils;

/**
 * A CacheWrapper that implements the DistributedTaskCapable interface, so it is capable of
 * executing a Callable against the cache using the DistributedExecutorService.
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */

public class InfinispanDistributedTask<K, V, T> implements DistributedTaskExecutor<T> {

   protected final Log log = LogFactory.getLog(getClass());
   protected final Infinispan52EmbeddedService service;

   public InfinispanDistributedTask(Infinispan52EmbeddedService service) {
      this.service = service;
   }

   @SuppressWarnings("unchecked")
   @Override
   public List<Future<T>> executeDistributedTask(ClassLoadHelper classLoadHelper, String distributedCallableFqn,
         String executionPolicyName, String failoverPolicyFqn, String nodeAddress, Map<String, String> params) {

      Cache<K, V> cache = (Cache<K, V>) service.getCache(null);
      DistributedExecutorService des = new DefaultExecutorService(cache);
      Callable<T> callable = null;
      DistributedTaskBuilder<T> taskBuilder = null;
      List<Future<T>> result = null;

      if (distributedCallableFqn == null) {
         log.fatal("The distributedCallableFqn parameter must be specified.");
      } else {
         try {
            callable = (Callable<T>) classLoadHelper.createInstance(distributedCallableFqn);
            taskBuilder = des.createDistributedTaskBuilder(callable);
            callable = (Callable<T>) Utils.invokeMethodWithString(callable, params);
         } catch (Exception e1) {
            throw (new IllegalArgumentException("Could not instantiate '" + distributedCallableFqn + "' as a Callable",
                  e1));
         }
      }

      if (callable != null) {
         if (executionPolicyName != null) {
            DistributedTaskExecutionPolicy executionPolicy = Enum.valueOf(DistributedTaskExecutionPolicy.class,
                  executionPolicyName);
            if (executionPolicy == null) {
               log.error("No DistributedTaskExecutionPolicy found with name: " + executionPolicyName);
            } else {
               taskBuilder = taskBuilder.executionPolicy(executionPolicy);
            }
         }

         if (failoverPolicyFqn != null) {
            try {
               DistributedTaskFailoverPolicy failoverPolicy = (DistributedTaskFailoverPolicy) classLoadHelper
                     .createInstance(failoverPolicyFqn);
               taskBuilder = taskBuilder.failoverPolicy(failoverPolicy);
            } catch (Exception e) {
               log.error("Could not instantiate DistributedTaskFailoverPolicy class: " + failoverPolicyFqn, e);
            }
         }

         if (nodeAddress != null) {
            Address target = findHostPhysicalAddress(nodeAddress);
            if (target == null) {
               log.error("No host found with address: " + nodeAddress);
            } else {
               result = new ArrayList<Future<T>>();
               result.add(des.submit(target, taskBuilder.build()));
            }
         } else {
            result = des.submitEverywhere(taskBuilder.build());
         }
      }

      return result;
   }

   private Address findHostPhysicalAddress(String nodeAddress) {
      Address result = null;
      Transport t = ((DefaultCacheManager) service.cacheManager).getTransport();
      if (t != null) {
         for (Address add : t.getPhysicalAddresses()) {
            if (add.toString().contains(nodeAddress)) {
               result = add;
               break;
            }
         }
      }
      return result;
   }

}

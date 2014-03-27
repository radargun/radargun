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
package org.radargun.stages.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.DefaultDistStageAck;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.CacheInformation;
import org.radargun.traits.InjectTrait;

/**
 * Paired with SingleTXLoadStage. Checks that the previous stage had the expected result.
 */
@Stage(doc = "Paired with SingleTXLoadStage. Checks that the previous stage had the expected result")
public class SingleTXCheckStage extends AbstractDistStage {

   private static final Pattern txValue = Pattern.compile("txValue(\\d*)@(\\d*-\\d*)");

   @Property(doc = "Indices of slaves which should have committed the transaction (others rolled back). Default is all committed.")
   private Set<Integer> commitSlave;

   @Property(doc = "Indices of threads which should have committed the transaction (others rolled back). Default is all committed.")
   private Set<Integer> commitThread;

   @Property(doc = "Expected size of the transcation.")
   private int transactionSize = 20;

   @Property(doc = "If this is set to true, REMOVE operation should have been executed. Default is false.")
   private boolean deleted = false;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private BasicOperations basicOperations;

   @InjectTrait
   private CacheInformation cacheInformation;
   
   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      if (slaves != null && !slaves.contains(slaveState.getSlaveIndex())) {
         return ack;
      }
      List<String> caches = new ArrayList<String>();
      if (cacheInformation == null) {
         caches.add(null);
      } else {
         caches.addAll(cacheInformation.getCacheNames());
      }

      for (String cacheName : caches) {
         if (cacheName != null) {
            log.info("Checking cache " + cacheName);
         }
         BasicOperations.Cache cache = basicOperations.getCache(cacheName);
    	   String committer = null;
	      for (int i = 0; i < transactionSize; ++i) {
	         try {
	            Object value = cache.get("txKey" + i);
	               if (!deleted) {
	               Matcher m;
	               if (value != null && value instanceof String && (m = txValue.matcher((String) value)).matches()) {
	                  if (Integer.parseInt(m.group(1)) != i) {
	                     exception(ack, "Unexpected value for txKey" + i + " = " + value, null);
	                     break;
	                  }
	                  if (committer == null) {                  
	                     committer = m.group(2);
	                     if (commitSlave != null) {
	                        boolean found = false;
	                        for (int slave : commitSlave) {
	                           if (committer.startsWith(String.valueOf(slave))) {
	                              found = true;
	                              break;
	                           }
	                        }
	                        if (!found) {
	                           exception(ack, "The transaction should be committed by slave " + commitSlave + " but commiter is " + committer, null);
	                           break;
	                        }
	                     }
	                     if (commitThread != null) {
	                        boolean found = false;
                           for (int slave : commitThread) {
                              if (committer.endsWith(String.valueOf(slave))) {
                                 found = true;
                                 break;
                              }
                           }
	                        if (!found) {
	                           exception(ack, "The transaction should be committed by thread " + commitThread + " but commiter is " + committer, null);
	                           break;
	                        }
	                     }
	                  } else if (!committer.equals(m.group(2))) {
	                     exception(ack, "Inconsistency: previous committer was " + committer + ", this is " + m.group(2), null);
	                     break;
	                  }
	               } else {
	                  exception(ack, "Unexpected value for txKey" + i + " = " + value, null);
	                  break;
	               }
	            } else {
	               if (value != null) {
	                  exception(ack, "The value for txKey" + i + " should have been deleted, is " + value, null);
	               }
	            }
	         } catch (Exception e) {
	            exception(ack, "Failed to get key txKey" + i, e);
	            break;
	         }
	      }
      }
      return ack;
   }

   private void exception(DefaultDistStageAck ack, String message, Exception e) {
      log.error(message, e);
      ack.setError(true);
      ack.setErrorMessage(message);
      if (e != null) {
         ack.setRemoteException(e);
      }
   }
}

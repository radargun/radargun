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

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Stage;
import org.radargun.features.XSReplicating;
import org.radargun.stages.helpers.ParseHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Paired with SingleTXLoadStage. Checks that the previous stage had the expected result.
 */
@Stage(doc = "Paired with SingleTXLoadStage. Checks that the previous stage had the expected result")
public class SingleTXCheckStage extends AbstractDistStage {

   private static final Pattern txValue = Pattern.compile("txValue(\\d*)@(\\d*-\\d*)");
   
   private Set<Integer> commitSlave;
   private Set<Integer> commitThread;
   private int transactionSize = 20;
   private boolean deleted = false;
   
   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      if (slaves != null && !slaves.contains(slaveIndex)) {
         return ack;
      }
      CacheWrapper cacheWrapper = slaveState.getCacheWrapper();
      List<String> caches = new ArrayList<String>();      
      if (cacheWrapper instanceof XSReplicating) {
    	  caches.add(((XSReplicating) cacheWrapper).getMainCache());
    	  caches.addAll(((XSReplicating) cacheWrapper).getBackupCaches());
      } else {
    	  caches.add(null); //the default cache
      }

      for (String cacheName : caches) {
          if (cacheName != null) {
        	  log.info("Checking cache " + cacheName);
          }
    	  String committer = null;    	  
	      for (int i = 0; i < transactionSize; ++i) {
	         try {
	            Object value = cacheWrapper.get(cacheName, "txKey" + i);
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

   public void setDeleted(boolean deleted) {
      this.deleted = deleted;
   }

   public void setCommitSlave(String commitSlave) {
      this.commitSlave = ParseHelper.parseSet(commitSlave, "commitSlave", log);
   }
   
   public void setCommitThread(String commitThread) {
      this.commitThread = ParseHelper.parseSet(commitThread, "commmitThread", log);
   }

   public void setTransactionSize(int transactionSize) {
      this.transactionSize = transactionSize;
   }
   
   @Override
   public String toString() {
	   return "SingleTXCheckStage(deleted=" + deleted + ", transactionSize=" + transactionSize + 
	         ", commitSlave=" + ParseHelper.toString(commitSlave, "all") + 
            ", commitThread=" + ParseHelper.toString(commitThread, "all") +
			   ", " + super.toString(); 
   }
}

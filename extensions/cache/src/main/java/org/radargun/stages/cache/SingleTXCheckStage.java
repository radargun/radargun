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
import org.radargun.traits.BasicOperations;
import org.radargun.traits.CacheInformation;
import org.radargun.traits.InjectTrait;

/**
 * Paired with SingleTXLoadStage. Checks that the previous stage had the expected result.
 */
@Stage(doc = "Paired with SingleTXLoadStage. Checks that the previous stage had the expected result")
public class SingleTXCheckStage extends AbstractDistStage {

   private static final Pattern TX_VALUE = Pattern.compile("txValue(\\d*)@(\\d*-\\d*)");

   @Property(doc = "Indices of slaves which should have committed the transaction (others rolled back). Default is all committed.")
   public Set<Integer> commitSlave;

   @Property(doc = "Indices of threads which should have committed the transaction (others rolled back). Default is all committed.")
   public Set<Integer> commitThread;

   @Property(doc = "Expected size of the transcation.")
   public int transactionSize = 20;

   @Property(doc = "If this is set to true, REMOVE operation should have been executed. Default is false.")
   public boolean deleted = false;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private BasicOperations basicOperations;

   @InjectTrait
   private CacheInformation cacheInformation;

   @Override
   public DistStageAck executeOnSlave() {
      if (!shouldExecute()) {
         return successfulResponse();
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
                  if (value != null && value instanceof String && (m = TX_VALUE.matcher((String) value)).matches()) {
                     if (Integer.parseInt(m.group(1)) != i) {
                        return errorResponse("Unexpected value for txKey" + i + " = " + value);
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
                              return errorResponse("The transaction should be committed by slave " + commitSlave + " but commiter is " + committer);
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
                              return errorResponse("The transaction should be committed by thread " + commitThread + " but commiter is " + committer);
                           }
                        }
                     } else if (!committer.equals(m.group(2))) {
                        return errorResponse("Inconsistency: previous committer was " + committer + ", this is " + m.group(2));
                     }
                  } else {
                     return errorResponse("Unexpected value for txKey" + i + " = " + value);
                  }
               } else {
                  if (value != null) {
                     return errorResponse("The value for txKey" + i + " should have been deleted, is " + value);
                  }
               }
            } catch (Exception e) {
               return errorResponse("Failed to get key txKey" + i, e);
            }
         }
      }
      return successfulResponse();
   }
}

package org.radargun.service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 */
public class Infinispan60ServerTopologyHistory extends AbstractTopologyHistory {
   protected final static String ALL_CACHES = "__all_caches__";

   protected final InfinispanServerService service;

   /**
    * Requires logging:
    * TRACE on org.infinispan.statetransfer.StateConsumerImpl,
    * TRACE on org.infinispan.statetransfer.StateTransferManagerImpl
    * DEBUG on org.infinispan.transaction.impl.TransactionTable
    */
   public Infinispan60ServerTopologyHistory(InfinispanServerService service) {
      this.service = service;
      /** we cannot detect changes for single cache - we have to track all of them as one {@link #ALL_CACHES} */
      final AtomicInteger topologyChangesOngoing = new AtomicInteger(0);
      final AtomicInteger hashChangesOngoing = new AtomicInteger(0);
      service.registerAction(Pattern.compile(".*Installing new cache topology.*"), new ProcessService.OutputListener() {
         @Override
         public void run(Matcher m) {
            if (topologyChangesOngoing.getAndIncrement() == 0) {
               log.debug("First topology change started");
               addEvent(topologyChanges, ALL_CACHES, true, 0, 0);
            } else {
               log.debug("Another topology change started");
            }
         }
      });
      service.registerAction(Pattern.compile(".*Topology changed, recalculating minTopologyId.*"), new ProcessService.OutputListener() {
         @Override
         public void run(Matcher m) {
            if (topologyChangesOngoing.decrementAndGet() == 0) {
               log.debug("All topology changes finished");
               addEvent(topologyChanges, ALL_CACHES, false, 0, 0);
            } else {
               log.debug("Another topology change finished");
            }
         }
      });
      service.registerAction(Pattern.compile(".*Lock State Transfer in Progress for topology ID.*"), new ProcessService.OutputListener() {
         @Override
         public void run(Matcher m) {
            if (hashChangesOngoing.getAndIncrement() == 0) {
               log.debug("First rehash started");
               addEvent(hashChanges, ALL_CACHES, true, 0, 0);
            } else {
               log.debug("Another rehash started");
            }
         }
      });
      service.registerAction(Pattern.compile(".*Unlock State Transfer in Progress for topology ID.*"), new ProcessService.OutputListener() {
         @Override
         public void run(Matcher m) {
            if (hashChangesOngoing.decrementAndGet() == 0) {
               log.debug("All rehashes finished");
               addEvent(hashChanges, ALL_CACHES, false, 0, 0);
            } else {
               log.debug("Another rehash finished");
            }
         }
      });
      service.lifecycle.addListener(new ProcessLifecycle.ListenerAdapter() {
         @Override
         public void afterStop(boolean graceful) {
            reset();
            topologyChangesOngoing.set(0);
            hashChangesOngoing.set(0);
         }
      });
   }

   @Override
   protected String getDefaultCacheName() {
      return ALL_CACHES;
   }
}

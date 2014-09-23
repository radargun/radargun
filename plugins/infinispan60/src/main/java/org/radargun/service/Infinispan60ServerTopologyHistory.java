package org.radargun.service;

import java.util.regex.Pattern;

/**
 */
public class Infinispan60ServerTopologyHistory extends AbstractTopologyHistory {
   protected final InfinispanServerService service;

   /**
    * Requires logging:
    * TRACE on org.infinispan.statetransfer.StateConsumerImpl,
    * TRACE on org.infinispan.statetransfer.StateTransferManagerImpl
    * DEBUG on org.infinispan.transaction.impl.TransactionTable
    */
   public Infinispan60ServerTopologyHistory(InfinispanServerService service) {
      this.service = service;
      service.registerAction(Pattern.compile(".*Installing new cache topology.*"), new Runnable() {
         @Override
         public void run() {
            log.debug("Topology change started");
            addEvent(topologyChanges, true, 0, 0);
         }
      });
      service.registerAction(Pattern.compile(".*Topology changed, recalculating minTopologyId.*"), new Runnable() {
         @Override
         public void run() {
            log.debug("Topology change finished");
            addEvent(topologyChanges, false, 0, 0);
         }
      });
      service.registerAction(Pattern.compile(".*Lock State Transfer in Progress for topology ID.*"), new Runnable() {
         @Override
         public void run() {
            log.debug("Rehash started");
            addEvent(hashChanges, true, 0, 0);
         }
      });
      service.registerAction(Pattern.compile(".*Unlock State Transfer in Progress for topology ID.*"), new Runnable() {
         @Override
         public void run() {
            log.debug("Rehash finished");
            addEvent(hashChanges, false, 0, 0);
         }
      });
      service.lifecycle.registerOnStop(new Runnable() {
         @Override
         public void run() {
            reset();
         }
      });
   }
}

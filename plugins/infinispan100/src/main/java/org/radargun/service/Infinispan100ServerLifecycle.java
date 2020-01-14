package org.radargun.service;

import java.util.regex.Pattern;

public class Infinispan100ServerLifecycle extends InfinispanServerLifecycle {

   public Infinispan100ServerLifecycle(InfinispanServerService service) {
      super(service);
   }

   protected Pattern getStartOK() {
      // [SERVER] ISPN080000: Infinispan Server starting
      return Pattern.compile(".*ISPN080000.*");
   }

   protected Pattern getStartError() {
      // ISPN80028: Infinispan Server failed to start
      return Pattern.compile(".*ISPN80028.*");
   }

   protected Pattern getStopped() {
      // [SERVER] ISPN080002: Infinispan Server stopping
      return Pattern.compile(".*ISPN080002.*");
   }
}

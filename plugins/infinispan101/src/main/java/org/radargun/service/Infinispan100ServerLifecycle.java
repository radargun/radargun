package org.radargun.service;

import java.util.regex.Pattern;

public class Infinispan100ServerLifecycle extends InfinispanServerLifecycle {

   public Infinispan100ServerLifecycle(InfinispanServerService service) {
      super(service);
   }

   protected Pattern getStartOK() {
      // ISPN080001: Infinispan Server started
      return Pattern.compile(".*ISPN080001.*");
   }

   protected Pattern getStartError() {
      // ISPN80028: Infinispan Server failed to start
      return Pattern.compile(".*ISPN80028.*");
   }

   protected Pattern getStopped() {
      // ISPN080003: Infinispan Server stopped
      return Pattern.compile(".*ISPN080003.*");
   }
}

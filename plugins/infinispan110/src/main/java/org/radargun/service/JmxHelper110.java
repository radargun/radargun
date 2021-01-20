package org.radargun.service;

import org.infinispan.configuration.global.GlobalConfiguration;

public class JmxHelper110 {

   private JmxHelper110() {

   }

   public static String getJmxDomain(GlobalConfiguration global) {
      return global.jmx().domain();
   }
}

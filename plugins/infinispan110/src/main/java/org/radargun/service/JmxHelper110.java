package org.radargun.service;

import org.infinispan.configuration.global.GlobalConfiguration;

public class JmxHelper110 {

   private JmxHelper110() {

   }

   // TODO after 11.0.0.Alpha1 replace with jmx instead of globalJmxStatistics
   public static String getJmxDomain(GlobalConfiguration global) {
      return global.globalJmxStatistics().domain();
   }
}

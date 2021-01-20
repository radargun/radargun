package org.radargun.service;

import org.infinispan.configuration.global.GlobalConfiguration;

public class JmxHelper101 {

   private JmxHelper101() {

   }

   public static String getJmxDomain(GlobalConfiguration global) {
      return global.globalJmxStatistics().domain();
   }
}

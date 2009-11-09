package org.cachebench.plugins


import apache.commons.logging.LogFactory
import java.net.URL
import java.util.Properties

object PluginLocator {
   val log = LogFactory.getLog(PluginLocator.getClass())
   
   def locatePlugin(): String = {
      // first see if there is an override provided by system properties.
      var wrapperName = System getProperty "cacheBenchFwk.cacheWrapperClassName"
      if (wrapperName == null || wrapperName.equals("")) {
         // search for 'registered' wrappers
         val enumeration: java.util.Enumeration[URL] = getClass().getClassLoader().getResources("cacheprovider.properties")
         if (enumeration != null && enumeration.hasMoreElements()) {
            val nextEl: URL = enumeration.nextElement()
            val props = new Properties()
            props.load(nextEl.openStream())
            wrapperName = props.getProperty("org.cachebenchfwk.wrapper")
         }
      }

      log trace "Picked up wrapper type as " + wrapperName

      return wrapperName
   }
}
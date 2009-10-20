package org.cachebench.local

//import org.cachebench.log.Loggable
import config.ConfigBuilder
import java.io.FileNotFoundException
import org.apache.commons.logging.LogFactory

/**
 * A TestRunner that runs the test in LOCAL mode.
 */

object LocalRunner {//{extends Loggable {
   val defaultConfigFile = "cachebench-local.xml"
   val log = LogFactory.getLog(this.getClass.getName)
   /**
    * LocalRunner takes in a few params.  Specifically, it should take in a configuration XML file, else use a default.
    */
   def main(args: Array[String]) {
      var configFile: String = null

      if (args.length == 1) {
         configFile = args(0)
      } else {
         configFile = defaultConfigFile
      }

      // if the file does NOT exist, throw an error and exit
      val confFile = ConfigBuilder.findConfigFile(configFile);
      if (confFile == null) throw new FileNotFoundException("Cannot find config file " + configFile);

      if (log isDebugEnabled) log debug "Starting a LOCAL mode benchmark, using benchmark framework configuration file " + configFile


      println("Hi there")
   }
}
package org.cachebench;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.config.ClusterConfig;
import org.cachebench.config.ConfigBuilder;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.io.IOException;

/**
 * Loads config file and checks whether the specified ports are available.
 * Can be used from a script, following exit codes are used:
 * <pre>
 *  0 - all ports are available
 *  1 - config file not found
 *  2 - a address is in use
 * </pre>
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
public class ClusterConfigurationCheck
{

   private static Log log = LogFactory.getLog("ClusterConfigurationCheck");

   public static void main(String[] args) throws Exception
   {
      String configFile = "cachebench.xml";
      if (args.length >= 1)
      {
         configFile = args[0];
      }
      URL configUrl = ConfigBuilder.findConfigFile(configFile);
      if (configUrl == null)
      {
         log.info("Could not find the config file, exiting with code 1");
         System.exit(1);
      }
      ClusterConfig config = ConfigBuilder.parseConfiguration(configUrl).getClusterConfig();
      boolean areSuspects = false;
      for (InetSocketAddress address : config.getMemberAddresses())
      {
         try
         {
            Socket sock = new Socket(address.getHostName(), address.getPort());
            areSuspects = true;
            log.info("Managed to connect to " + address);
         } catch (IOException e)
         {
            log.trace("Connection to : " +  address + " failed; expected behavior");
         }
      }
      if (!areSuspects)
      {
         log.info("Success (could not establish any connection)");
      }
      System.exit(areSuspects ? 2 : 0);
   }
}

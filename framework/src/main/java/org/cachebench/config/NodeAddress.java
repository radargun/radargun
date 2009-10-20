package org.cachebench.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
public class NodeAddress
{
   private String host;

   private String port;

   public String getHost()
   {
      return host;
   }

   public void setHost(String host)
   {
      this.host = host;
   }

   public String getPort()
   {
      return port;
   }

   public void setPort(String port)
   {
      this.port = port;
   }

   public int getPort(int defaultValue)
   {
      int portInt = Integer.parseInt(port);
      if (portInt <= 0)
      {
         return defaultValue;
      }
      else
      {
         return portInt;
      }
   }

   public String toString()
   {
      return host + ':' + port;
   }

   public int getPortAsInt()
   {
      return Integer.parseInt(port);
   }
}

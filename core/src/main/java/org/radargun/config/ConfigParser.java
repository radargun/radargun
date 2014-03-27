package org.radargun.config;

/**
 * Abstracts the logic of parsing an configuration file.
 *
 * @author Mircea.Markus@jboss.com
 */
public abstract class ConfigParser {

   public abstract MasterConfig parseConfig(String config) throws Exception;

   public static ConfigParser getConfigParser() {
      return new DomConfigParser();
   }
}

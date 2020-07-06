package org.radargun.config;

/**
 * Abstracts the logic of parsing an configuration file.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public abstract class ConfigParser {

   public abstract MainConfig parseConfig(String config) throws Exception;

   public static ConfigParser getConfigParser() {
      return new DomConfigParser();
   }
}

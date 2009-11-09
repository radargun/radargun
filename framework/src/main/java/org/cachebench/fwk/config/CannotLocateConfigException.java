package org.cachebench.fwk.config;

/**
 * Thrown if a config file cannot be located
 *
 * @author Manik Surtani
 */
public class CannotLocateConfigException extends Exception {
   public CannotLocateConfigException(String configFile) {
      super("Cannot locate config file ["+configFile+"] either on the file system or classpath!");
   }
}

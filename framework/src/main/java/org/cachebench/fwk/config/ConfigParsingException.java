package org.cachebench.fwk.config;

/**
 * Thrown when a config file cannot be parsed
 *
 * @author Manik Surtani
 */
public class ConfigParsingException extends Exception {
   public ConfigParsingException() {
   }

   public ConfigParsingException(String message) {
      super(message);
   }

   public ConfigParsingException(String message, Throwable cause) {
      super(message, cause);
   }

   public ConfigParsingException(Throwable cause) {
      super(cause);
   }
}

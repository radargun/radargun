package org.radargun.exception;

/**
 * Some plugins have classes that were removed in new versions.
 * The properly way to keep the backward compatibility is using reflection to instantiate the classes in Runtime.
 * This takes time and it is error prone.
 *
 * Instead of removing the hole plugin, let's deprecate some methods.
 *
 * Example: DistributedExecutorService were removed in Infinispan10 and then we cannot execute some methods
 */
public class PluginNotSupportedException extends RuntimeException {

   /**
    * Why you are dropping the plugin support? What is the reason?
    *
    * @param reason a friendly message
    */
   public PluginNotSupportedException(String reason) {

   }
}

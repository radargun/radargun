package org.radargun;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * // TODO: Document this
 *
 * @author mmarkus
 * @since 4.0
 */
public class ShutDownHook extends Thread {

   private static Log log = LogFactory.getLog(ShutDownHook.class);

   private static volatile boolean controlled = false;

   private String processDescription;

   public ShutDownHook(String processDescription) {
      this.processDescription = processDescription;
   }

   @Override
   public void run() {
      if (controlled) {
         log.info(processDescription + " is being shutdown");
      } else {
         log.warn(processDescription + ": unexpected shutdown!");
      }
   }

   public static void exit(int code) {
      controlled = true;
      System.exit(code);
   }
}

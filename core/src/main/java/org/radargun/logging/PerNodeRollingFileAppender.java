package org.radargun.logging;

import org.apache.log4j.RollingFileAppender;

/**
 * Apends an node instance identifier at the end of the filename.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class PerNodeRollingFileAppender extends RollingFileAppender {

   public static final String PROP_NAME = "log4j.file.prefix";

   @Override
   public void setFile(String s) {
      super.setFile(appendNodeIndex(s));
   }

   private String appendNodeIndex(String s) {
      String prop = System.getProperty(PROP_NAME);
      if (prop != null) {
         System.out.println("PerNodeRollingFileAppender::Using file prefix:" + prop);
         return prop + "_" + s;
      } else {
         System.out.println("PerNodeRollingFileAppender::Not using file prefix.");
         return s;
      }
   }

}

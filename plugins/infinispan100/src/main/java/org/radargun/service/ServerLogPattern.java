package org.radargun.service;

import java.util.regex.Pattern;

/**
 * Log patterns which will be used by InfinispanServerLifecycle
 * @author Gustavo Lira &lt;glira@redhat.com&gt;
 */
public enum ServerLogPattern {

   START_OK(".*\\[org\\.infinispan\\.SERVER\\].*started in.*"),
   START_ERROR(".*\\[org\\.infinispan\\.SERVER\\].*Infinispan Server stopping.*"),
   STOPPED(".*\\[org\\.infinispan\\.SERVER\\].*stopped.*");

   String pattern;

   ServerLogPattern(String pattern) {
      this.pattern = pattern;
   }

   public Pattern getPattern() {
      return Pattern.compile(pattern);
   }

}

package org.radargun.features;

import org.radargun.CacheWrapper;

/**
 * 
 * Additional CacheWrapper feature, allows simulation of abrupt cache shutdown (node crash).
 * 
 * @author Michal Linhard <mlinhard@redhat.com>
 */
public interface Killable extends CacheWrapper {
   /**
    * 
    * Kill the CacheWrapper instance abruptly.
    * 
    * @throws Exception
    */
   void kill() throws Exception;
   void killAsync() throws Exception;

}

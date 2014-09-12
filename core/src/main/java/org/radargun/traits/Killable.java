package org.radargun.traits;

/**
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 */
@Trait(doc = "Allows simulation of abrupt shutdown (node crash).")
public interface Killable {
   /**
    * Kill the Service instance abruptly.
    */
   void kill();
   
   /**
    * Wait until the Service is in the state where it can be killed, but then kill it in different thread.
    */
   void killAsync();
}

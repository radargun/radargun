package org.radargun.utils;

/**
 * Class that stores timestamp.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface Timestamped {
   /**
    * @return Stored (epoch time) timestamp, in milliseconds.
    */
   long getTimestamp();
}

package org.radargun.utils;

/**
 * Class that stores timestamp.
 */
public interface Timestamped {
   /**
    * @return Stored (epoch time) timestamp, in milliseconds.
    */
   long getTimestamp();
}

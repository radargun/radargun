package org.radargun.config;

import java.io.Serializable;

/**
 * Common supertype for any definition of a property.
 */
public interface Definition extends Serializable {
   /**
    * @param other
    * @return Definition that is based on this definition, but updated by other definition.
    */
   Definition apply(Definition other);
}

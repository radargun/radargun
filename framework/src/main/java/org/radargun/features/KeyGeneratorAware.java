package org.radargun.features;

import org.radargun.CacheWrapper;
import org.radargun.stressors.KeyGenerator;

/**
 * Cache wrapper implementing this interface is supposed to produce its own key generator.
 *
 * @author Martin Gencur
 */
public interface KeyGeneratorAware extends CacheWrapper {

   KeyGenerator getKeyGenerator(int keyBufferSize);

}

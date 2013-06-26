package org.radargun.stressors;

/**
 * Used for generating key used by {@link PutGetStressor}. All implementations must have an default/no-arg public
 * constructor.
 * <p/>
 * Concurrency: methods of this class might be called from multiple threads concurrently.
 *
 * @author Mircea.Markus@jboss.com
 */
public interface KeyGenerator {

   String KEY_GENERATOR = "KEY_GENERATOR";

   void init(String param);

   /**
    * @param keyIndex
    * @return
    */
   Object generateKey(long keyIndex);
}

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

   /**
    * Called for distributed benchmarks.
    */
   Object generateKey(int nodeIndex, int threadIndex, int keyIndex);

   /**
    * Called for local benchmarks.
    */
   Object generateKey(int threadIndex, int keyIndex);
}

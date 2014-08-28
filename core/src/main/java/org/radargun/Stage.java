package org.radargun;

/**
 * A stage is a step in the benchmark process. E.g. of stages are starting cache wrapper, warmup, run actual test etc.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public interface Stage {
   String STAGE = "Stage";

   String getName();

   boolean isExitOnFailure();
}

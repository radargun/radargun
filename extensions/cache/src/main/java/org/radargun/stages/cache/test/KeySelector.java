package org.radargun.stages.cache.test;

/**
 * Provides key IDs in certain pattern
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface KeySelector {
   long next();
}

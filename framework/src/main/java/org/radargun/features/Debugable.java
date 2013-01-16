package org.radargun.features;

/**
 * Feature for wrappers supporting debug info output. An example of implementation for debugKey could be to enable
 * full tracing and do a GET operation.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @since 1/11/13
 */
public interface Debugable {
   void debugKey(String bucket, String key);
   void debugInfo(String bucket);
}

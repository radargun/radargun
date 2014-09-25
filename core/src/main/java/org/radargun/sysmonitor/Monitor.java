package org.radargun.sysmonitor;

/**
 * // TODO: Document this
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface Monitor extends Runnable {
   void start();
   void stop();
}

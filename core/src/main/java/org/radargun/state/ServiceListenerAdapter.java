package org.radargun.state;

/**
 * No-op implementation (for convenience when overriding only one method)
 */
public class ServiceListenerAdapter implements ServiceListener {
   @Override
   public void beforeServiceStart() {}

   @Override
   public void afterServiceStart() {}

   @Override
   public void beforeServiceStop(boolean graceful) {}

   @Override
   public void afterServiceStop(boolean graceful) {}

   @Override
   public void serviceDestroyed() {}
}

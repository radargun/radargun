package org.radargun.service;

import org.radargun.Service;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;

@Service(doc = EchoService.SERVICE_DESCRIPTION)
public class EchoService implements Lifecycle {
   protected final Log log = LogFactory.getLog(getClass());
   protected static final String SERVICE_DESCRIPTION = "Echo Service";

   private boolean started;

   @Override
   public void start() {
      started = true;
      log.infof("Echo service started");
   }

   @Override
   public void stop() {
      started = false;
      log.infof("Echo service stopped");
   }

   @ProvidesTrait
   public Lifecycle createLifecycle() {
      return this;
   }

   @Override
   public boolean isRunning() {
      return started;
   }
}

package org.radargun.listeners;

import com.tangosol.net.MemberEvent;
import com.tangosol.net.MemberListener;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.service.Coherence3Service;

public class MemberListenerImpl implements MemberListener {
   private final Log log = LogFactory.getLog(MemberListenerImpl.class);
   private final Coherence3Service service;

   public MemberListenerImpl(Coherence3Service service) {
      this.service = service;
   }

   @Override
   public void memberJoined(MemberEvent event) {
      log.info(event.toString());
      service.updateMembership(event);
   }

   @Override
   public void memberLeaving(MemberEvent event) {
      log.info(event.toString());
      service.updateMembership(event);
   }

   @Override
   public void memberLeft(MemberEvent event) {
      log.info(event.toString());
      service.updateMembership(event);
   }
}

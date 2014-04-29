package org.radargun.listeners;

import com.tangosol.net.MemberEvent;
import com.tangosol.net.MemberListener;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

public class MemberListenerImpl implements MemberListener {
   
   private Log log = LogFactory.getLog(MemberListenerImpl.class);
   
   public MemberListenerImpl() {     
   }

   @Override
   public void memberJoined(MemberEvent event) {
      log.info(event.toString());
   }

   @Override
   public void memberLeaving(MemberEvent event) {
      log.info(event.toString());
   }

   @Override
   public void memberLeft(MemberEvent event) {
      log.info(event.toString());
   }
   

}

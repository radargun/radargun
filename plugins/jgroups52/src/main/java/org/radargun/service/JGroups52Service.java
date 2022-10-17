package org.radargun.service;

import org.jgroups.Receiver;
import org.jgroups.View;
import org.radargun.Service;

@Service(doc = "JGroupsService faking cache operations")
public class JGroups52Service extends JGroups42Service {

   protected void createCacheOperation() {
      jGroupsCacheOperation = new JGroups52CacheOperation(ch);
   }

   protected void setReceiver() {
      ch.setReceiver(new Receiver() {
         @Override
         public void viewAccepted(View newView) {
            receiver.viewAccepted(newView);
         }
      });
   }

   @Override
   protected void connectChannel(String clusterName) throws Exception {
      ch.connect(clusterName);
   }
}

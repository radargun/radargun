package org.radargun.service;

import org.jgroups.Receiver;
import org.jgroups.View;
import org.radargun.Service;
import org.radargun.config.Property;

@Service(doc = "JGroupsService faking cache operations")
public class JGroups52Service extends JGroups42Service {

   @Property(doc = "Simulate ISPN version. Default 14")
   protected String ispnVersion = "14";

   protected void createCacheOperation() {
      jGroupsCacheOperation = new JGroups52ISPN14CacheOperation(ch);
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

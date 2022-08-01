package org.radargun.service;

import org.jgroups.JChannel;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.blocks.RpcDispatcher;
import org.radargun.Service;

@Service(doc = "JGroupsService faking cache operations")
public class JGroups52Service extends JGroups36Service {

   @Override
   protected RpcDispatcher createRpcDispatcher(JChannel ch) {
      RpcDispatcher disp = new RpcDispatcher(ch, this);
      disp.setMethodLookup(id -> METHODS[id]);
      disp.setReceiver(new Receiver() {
         @Override
         public void viewAccepted(View newView) {
            JGroups52Service.this.receiver.viewAccepted(newView);
         }
      });
      return disp;
   }

   @Override
   protected void connectChannel(String clusterName) throws Exception {
      ch.connect(clusterName);
   }
}

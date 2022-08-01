package org.radargun.service;

import org.jgroups.JChannel;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.jgroups.blocks.RpcDispatcher;
import org.radargun.Service;

@Service(doc = "JGroupsService faking cache operations")
public class JGroups42Service extends JGroups36Service {

   @Override
   protected RpcDispatcher createRpcDispatcher(JChannel ch) {
      RpcDispatcher disp = new RpcDispatcher(ch, this);
      disp.setMembershipListener(new ReceiverAdapter() {
         @Override
         public void viewAccepted(View view) {
            JGroups42Service.this.receiver.viewAccepted(view);
         }
      });
      disp.setMethodLookup(id -> METHODS[id]);
      return disp;
   }

   @Override
   protected void connectChannel(String clusterName) throws Exception {
      ch.connect(clusterName);
   }
}

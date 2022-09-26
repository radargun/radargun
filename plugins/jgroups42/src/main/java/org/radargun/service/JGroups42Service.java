package org.radargun.service;

import org.jgroups.JChannel;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.jgroups.blocks.RpcDispatcher;
import org.radargun.Service;
import org.radargun.config.PropertyDelegate;
import org.radargun.stages.trace.TraceMethodCall;

@Service(doc = "JGroupsService faking cache operations")
public class JGroups42Service extends JGroups36Service {

   @PropertyDelegate(prefix = "trace.")
   private TraceMethodCall trace = new TraceMethodCall();

   @Override
   public void start() {
      if (trace.getClassName() != null) {
         trace.start();
      }
      super.start();
   }

   @Override
   public void stop() {
      if (trace.getClassName() != null) {
         trace.dump();
      }
      super.stop();
   }

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

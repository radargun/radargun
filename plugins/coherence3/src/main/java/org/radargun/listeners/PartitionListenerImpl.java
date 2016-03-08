package org.radargun.listeners;

import com.tangosol.net.partition.PartitionEvent;
import com.tangosol.net.partition.PartitionListener;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

public class PartitionListenerImpl implements PartitionListener {

   private Log log = LogFactory.getLog(PartitionListenerImpl.class);

   public PartitionListenerImpl() {
   }

   @Override
   public void onPartitionEvent(PartitionEvent event) {
      log.debug(event.toString());
   }

}

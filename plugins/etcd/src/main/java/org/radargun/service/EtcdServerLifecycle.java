package org.radargun.service;

import java.io.IOException;

import org.radargun.utils.Utils;

public class EtcdServerLifecycle extends ProcessLifecycle<EtcdServerService> {

   public EtcdServerLifecycle(EtcdServerService service) {
      super(service);
   }

   @Override
   public void start() {
      try {
         if (service.distributionZip != null) {
            Utils.unzip(service.distributionZip, service.distributionDir);
            // the extraction erases the executable bits
            Utils.setPermissions(service.distributionDir + "/etcd", "rwxr-xr-x");
            Utils.setPermissions(service.distributionDir + "/etcdctl", "rwxr-xr-x");
         }
      } catch (IOException e) {
         throw new RuntimeException("Failed to prepare etcd distribution!", e);
      }
      super.start();
   }
}

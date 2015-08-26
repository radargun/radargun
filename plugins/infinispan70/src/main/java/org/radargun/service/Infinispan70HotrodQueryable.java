package org.radargun.service;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.radargun.utils.Utils;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Matej Cimbora
 */
public class Infinispan70HotrodQueryable extends InfinispanHotrodQueryable {

   private static String PROTOBUF_METADATA_CACHE_NAME = "___protobuf_metadata";

   public Infinispan70HotrodQueryable(Infinispan60HotrodService service) {
      super(service);
   }

   @Override
   protected void registerProtofilesLocal(SerializationContext context) {
      try {
         context.registerProtoFiles(FileDescriptorSource.fromResources(service.protofiles));
      } catch (IOException e) {
         log.error("Exception while registering protofiles", e);
         throw new IllegalStateException(e);
      }
   }

   @Override
   protected void registerProtofilesRemote() {
      for (String protofile : service.protofiles) {
         String descriptor = null;
         try (InputStream is = getClass().getResourceAsStream(protofile)) {
            descriptor = Utils.readAsString(is);
         } catch (IOException e) {
            throw new IllegalStateException("Failed to read protofile " + protofile, e);
         }
         try {
            RemoteCache<String, String> metadataCache = service.managerForceReturn.getCache(PROTOBUF_METADATA_CACHE_NAME);
            metadataCache.put(protofile, descriptor);
            log.info("Protofile " + protofile + " registered.");
         } catch (Exception e) {
            throw new IllegalStateException("Failed to register protofile " + protofile, e);
         }
      }
   }

}

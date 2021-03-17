package org.radargun.service;

import okhttp3.Credentials;
import okhttp3.Request;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.query.remote.client.ProtobufMetadataManagerConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Infinispan121ServerLifecycle extends Infinispan110ServerLifecycle {

   private static final String PROTOBUF_METADATA_CACHE_NAME = "___protobuf_metadata";

   Infinispan121ServerService service;
   public Infinispan121ServerLifecycle(Infinispan121ServerService service) {
      super(service);
      this.service = service;
   }

   @Override
   protected void fireAfterStart() {
      super.fireAfterStart();
      if (!this.service.getLifecycle().isRunning()) {
         throw new IllegalStateException("The server should be running");
      }

      if(this.service.getProtobufFile() != null) {
         try {
            ConfigurationBuilder builder = new ConfigurationBuilder();
            builder.addServer().host("localhost").port(this.service.getDefaultServerPort())
                    .security().authentication().enable().username(this.service.getUsername()).password(this.service.getPassword());

            RemoteCache<String, String> metadata = new RemoteCacheManager(builder.build()).getCache(PROTOBUF_METADATA_CACHE_NAME);

            Stream<String> lines = Files.lines(Paths.get(this.service.getProtobufFile()));
            String protobufContent = lines.collect(Collectors.joining(System.lineSeparator()));

            metadata.putIfAbsent("library.proto", protobufContent);
            log.info("Protobuf schema registered.");
         } catch (IOException e) {
            log.error("Something went wrong during protobuf registration", e);
         }
      }
   }
}

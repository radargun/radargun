package org.radargun.service;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.infinispan.client.rest.RestResponse;
import org.infinispan.client.rest.configuration.RestClientConfigurationBuilder;
import org.infinispan.client.rest.impl.okhttp.RestClientOkHttp;
import org.infinispan.server.network.NetworkAddress;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * <p> RestClient to consume Infinispan REST API to interact with the Cache Manager and obtain cluster and usage statistics. </p>
 * <p> @see <a href="https://github.com/infinispan/infinispan/blob/main/documentation/src/main/asciidoc/topics/rest_api_v2.adoc#cache-manager">Infinispan REST API</a> </p>
 *
 * @author Gustavo Lira &lt;glira@redhat.com&gt;
 */
public class InfinispanRestAPI {

   // ms
   private static final int DEFAULT_TIMEOUT = 5_000;

   protected final Log log = LogFactory.getLog(getClass());

   private final ObjectMapper mapper;
   private final RestClientOkHttp restClientOkHttp;
   private final String cacheManagerName;

   public InfinispanRestAPI(Integer serverPort, String username, String password) throws IOException {
      this.mapper = new ObjectMapper();
      this.mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
      this.mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
      this.cacheManagerName = System.getProperty("radargun.infinispan.cacheManagerName", "clustered");
      RestClientConfigurationBuilder config = new RestClientConfigurationBuilder();
      config.socketTimeout(DEFAULT_TIMEOUT)
              .connectionTimeout(DEFAULT_TIMEOUT)
              .addServer()
              .host(lookupServerHost())
              .port(serverPort);
      if (username != null && password != null) {
         config.security().authentication().enable().username(username).password(password);
      }

      this.restClientOkHttp = new RestClientOkHttp(config.build());
   }

   private String lookupServerHost() throws IOException {
      // the following are predefined values in the INFINISPAN10 XML Files
      String interfaceName = System.getProperty("radargun.infinispan.interfaceName", "public");
      String bindAddress = System.getProperty("infinispan.bind.address", "127.0.0.1");

      // given a config, search the network address
      NetworkAddress networkServerAddress = NetworkAddress.fromString(interfaceName, bindAddress);
      String host = networkServerAddress.getAddress().getHostAddress();

      log.info(String.format("interface: %s, bindAddress: %s, host: %s", interfaceName, bindAddress, host));

      return host;
   }

   public CacheManagerInfo getCacheManager() throws RestException {
      CacheManagerInfo cacheManagerInfo;
      CompletableFuture<RestResponse> responseFuture = restClientOkHttp.cacheManager(this.cacheManagerName).info().toCompletableFuture();
      RestResponse response;
      try {
         response = responseFuture.get(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);
         String body = response.getBody();
         try {
            cacheManagerInfo = mapper.readValue(body, CacheManagerInfo.class);
         } catch (JsonProcessingException e) {
            log.error("Http status: " + response.getStatus() + ", Http body: " + body);
            throw new RestException("Cannot parse the response", e);
         }
      } catch (InterruptedException | ExecutionException | TimeoutException e) {
         throw new RestException("Cannot retrieve the response", e);
      }
      return cacheManagerInfo;
   }

   public void stopCluster(long stopTimeout) {
      try {
         restClientOkHttp.cluster().stop().toCompletableFuture().get(stopTimeout, TimeUnit.MILLISECONDS);
      } catch (InterruptedException | ExecutionException | TimeoutException e) {
         // the stage should be responsible to check if the node still alive
      }
   }

   public void info() throws ExecutionException, InterruptedException {
      restClientOkHttp.server().info().toCompletableFuture().get();
   }

   static class RestException extends Exception {
      public RestException(String message, Exception e) {
         super(message, e);
      }
   }

}

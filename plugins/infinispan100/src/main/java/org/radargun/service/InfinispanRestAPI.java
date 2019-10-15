package org.radargun.service;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
 * <p> @see <a href="https://github.com/infinispan/infinispan/blob/master/documentation/src/main/asciidoc/topics/rest_api_v2.adoc#cache-manager">Infinispan REST API</a> </p>
 *
 * @author Gustavo Lira &lt;glira@redhat.com&gt;
 */
public class InfinispanRestAPI {

   // ms
   private static final int DEFAULT_TIMEOUT = 1_000;

   protected final Log log = LogFactory.getLog(getClass());

   private final ObjectMapper mapper;
   private final RestClientOkHttp restClientOkHttp;
   private final String cacheManagerName;

   public InfinispanRestAPI(Integer serverPort) {
      this.mapper = new ObjectMapper();
      this.mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
      this.mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
      this.cacheManagerName = System.getProperty("radargun.infinispan.cacheManagerName", "clustered");
      this.restClientOkHttp = new RestClientOkHttp(new RestClientConfigurationBuilder()
            .socketTimeout(DEFAULT_TIMEOUT).connectionTimeout(DEFAULT_TIMEOUT)
            .addServer().host(lookupServerHost()).port(serverPort).build());
   }

   private String lookupServerHost() {
      // the following are predefined values in the INFINISPAN10 XML Files
      String interfaceName = System.getProperty("radargun.infinispan.interfaceName", "public");
      String bindAddress = System.getProperty("infinispan.bind.address", "127.0.0.1");

      // given a config, search the network address
      NetworkAddress networkServerAddress = NetworkAddress.fromString(interfaceName, bindAddress);
      String host = networkServerAddress.getAddress().getHostAddress();

      log.info(String.format("interface: %s, bindAddress: %s, host: %s", interfaceName, bindAddress, host));

      return host;
   }

   CacheManagerInfo getCacheManager() throws RestException {
      CacheManagerInfo cacheManagerInfo;
      try {
         RestResponse response = restClientOkHttp.cacheManager(this.cacheManagerName).info().toCompletableFuture()
               .get(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);
         cacheManagerInfo = mapper.readValue(response.getBodyAsStream(), CacheManagerInfo.class);
         log.info(cacheManagerInfo.toString());
      } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
         log.error("Cannot access the cache manager: " + e.getMessage());
         throw new RestException(e);
      }
      return cacheManagerInfo;
   }

   static class RestException extends Exception {
      public RestException(Exception e) {
         super(e);
      }
   }

}

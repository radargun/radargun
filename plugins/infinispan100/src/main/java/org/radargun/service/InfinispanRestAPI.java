package org.radargun.service;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * <p> RestClient to consume Infinispan REST API to interact with the Cache Manager and obtain cluster and usage statistics. </p>
 * <p> @see <a href="https://github.com/infinispan/infinispan/blob/master/documentation/src/main/asciidoc/topics/rest_api_v2.adoc#cache-manager">Infinispan REST API</a> </p>
 *
 * @author Gustavo Lira &lt;glira@redhat.com&gt;
 */
public class InfinispanRestAPI {

   private ObjectMapper mapper;
   private HttpClient httpClient;
   private HttpResponse<String> response;
   private String defaultPort;
   private static final String CACHE_MANAGER_RESOURCE = "v2/cache-managers/clustered";
   private String serverIp;
   private final Log log = LogFactory.getLog(getClass());

   public InfinispanRestAPI(String defaultPort) {
      mapper = new ObjectMapper();
      mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
      mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
      httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
      this.defaultPort = defaultPort;
      try {
         serverIp = Inet4Address.getLocalHost().getHostAddress();
      } catch (UnknownHostException e) {
         log.error("Wasn't possible to get HostAddress to execute Rest Operations", e);
         throw new RuntimeException(e);
      }
   }

   public CacheManagerInfo getCacheManager() {
      String url = String.format("http://%s:%s/rest/%s", serverIp, this.defaultPort, CACHE_MANAGER_RESOURCE);
      HttpRequest get = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();

      CacheManagerInfo cacheManagerInfo = null;
      try {
         response = httpClient.send(get, HttpResponse.BodyHandlers.ofString());
         String json = response.body();
         cacheManagerInfo = mapper.readValue(json, CacheManagerInfo.class);
      } catch (IOException e) {
         e.printStackTrace();
      } catch (InterruptedException e) {
         e.printStackTrace();
      }
      return cacheManagerInfo;
   }

}

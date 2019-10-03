package org.radargun.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.URL;
import java.net.UnknownHostException;

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
   private Integer defaultPort;
   private static final String CACHE_MANAGER_RESOURCE = "v2/cache-managers/clustered";
   private String serverIp;
   private final Log log = LogFactory.getLog(getClass());

   public InfinispanRestAPI(Integer defaultPort) {
      mapper = new ObjectMapper();
      mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
      mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
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

      CacheManagerInfo cacheManagerInfo = null;
      try {
         String json = doGet(url);
         cacheManagerInfo = mapper.readValue(json, CacheManagerInfo.class);
      } catch (IOException e) {
         e.printStackTrace();
      }
      return cacheManagerInfo;
   }

   private String doGet(String url) throws IOException {
      BufferedReader in = null;
      StringBuilder content = new StringBuilder();
      try {
         HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
         con.setRequestMethod("GET");
         con.setConnectTimeout(5000);
         con.setReadTimeout(5000);
         int status = con.getResponseCode();
         if (status != 200) {
            content.append("ERROR: ").append(status).append("\n");
         }
         in = new BufferedReader(new InputStreamReader(con.getInputStream()));
         String inputLine;
         while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
         }
      } finally {
         if (in != null) {
            in.close();
         }
      }
      return content.toString();
   }

}

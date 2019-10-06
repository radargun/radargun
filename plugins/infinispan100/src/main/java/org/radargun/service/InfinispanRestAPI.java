package org.radargun.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

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

   protected final Log log = LogFactory.getLog(getClass());

   private ObjectMapper mapper;
   private Integer defaultPort;
   private static final String CACHE_MANAGER_RESOURCE = "v2/cache-managers/clustered";
   private String serverIp;

   public InfinispanRestAPI(Integer defaultPort) {
      this.mapper = new ObjectMapper();
      this.mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
      this.mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
      this.defaultPort = defaultPort;
      this.serverIp = lookupServerIp();
   }

   private String lookupServerIp() {
      // the following are predefined values in the INFINISPAN10 XML Files
      String interfaceName = System.getProperty("radargun.infinispan.interfaceName", "public");
      String bindAddress = System.getProperty("infinispan.bind.address", "127.0.0.1");

      // given a config, search the network address
      NetworkAddress networkServerAddress = NetworkAddress.fromString(interfaceName, bindAddress);
      String host = networkServerAddress.getAddress().getHostAddress();

      log.info(String.format("interface: %s, bindAddress: %s, host: %s", interfaceName, bindAddress, host));

      return host;
   }

   public CacheManagerInfo getCacheManager() {
      String url = String.format("http://%s:%s/rest/%s", serverIp, this.defaultPort, CACHE_MANAGER_RESOURCE);
      CacheManagerInfo cacheManagerInfo = null;
      try {
         String json = doGet(url);
         cacheManagerInfo = mapper.readValue(json, CacheManagerInfo.class);
      } catch (IOException e) {
         log.error(String.format("Cannot access: %s -> %s", url, e.getMessage()));
      }
      return cacheManagerInfo;
   }

   public static String doGet(String url) throws IOException {
      return doGet(url, 200);
   }

   public static String doGet(String url, int expectedHttpStatusResponse) throws IOException {

      StringBuilder content = new StringBuilder();
      int httpResponseStatus = -1;
      int count = 0;

      // network is not reliable
      while (httpResponseStatus != expectedHttpStatusResponse && count++ < 5) {
         BufferedReader in = null;
         content = new StringBuilder();
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
      }
      return content.toString();
   }

}

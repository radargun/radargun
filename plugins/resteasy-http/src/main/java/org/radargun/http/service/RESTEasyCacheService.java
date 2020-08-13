package org.radargun.http.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.ProvidesTrait;

/**
 * REST client service using the JAX-RS 2.0 client api with the RESTEasy engine based on
 * java.net.HttpURLConnection
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Service(doc = "RestEasy REST client for Cache")
public class RESTEasyCacheService extends AbstractRESTEasyService {
   private static final Log log = LogFactory.getLog(RESTEasyCacheService.class);

   @Property(doc = "The default path on the server for the REST service. Defaults to 'rest'.")
   private String rootPath = "rest";

   @Property(doc = "Expected cache name. Requests for other caches will fail. Defaults to 'default'.")
   protected String cacheName = "default";

   // Used to load balance requests across servers
   protected AtomicInteger nextIndex = new AtomicInteger(0);

   @ProvidesTrait
   public RESTEasyCacheInfo createCacheInfo() {
      return new RESTEasyCacheInfo(this);
   }

   @ProvidesTrait
   public RESTEasyCacheOperations createOperations() {
      return new RESTEasyCacheOperations(this);
   }

   public String getRootPath() {
      return rootPath;
   }

   /**
    * Distributes HTTP requests among servers.
    * 
    * @return InetSocketAddress of the next server to use.
    */
   public InetSocketAddress nextServer() {
      return getServers().get((nextIndex.getAndIncrement() & Integer.MAX_VALUE) % getServers().size());
   }

   String buildUrl(Object key) {
      StringBuilder str = new StringBuilder(buildCacheUrl(nextServer(), getRootPath(), getUsername(), getPassword(), cacheName));
      if (key != null) {
         str.append("/").append(key);
      }
      if (log.isTraceEnabled()) {
         log.trace("buildUrl(Object key) = " + str);
      }

      return str.toString();
   }

   Object decodeByteArray(byte[] bytes) throws IOException, ClassNotFoundException {
      if (bytes != null) {
         ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
         ObjectInputStream ois = new ObjectInputStream(bin);
         try {
            return ois.readObject();
         } finally {
            if (bin != null) {
               bin.close();
            }
         }
      }
      return null;
   }

   String buildCacheUrl(String cache) {
      return buildCacheUrl(nextServer(), getRootPath(), getUsername(), getPassword(), cache);
   }
}

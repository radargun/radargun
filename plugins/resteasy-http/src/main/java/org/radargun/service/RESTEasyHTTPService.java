package org.radargun.service;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.resteasy.client.jaxrs.BasicAuthentication;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder.HostnameVerificationPolicy;
import org.jboss.resteasy.client.jaxrs.engines.URLConnectionEngine;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.RESTAddressListConverter;
import org.radargun.utils.TimeConverter;

/**
 * REST client service using the JAX-RS 2.0 client api with the RESTEasy engine based on
 * java.net.HttpURLConnection
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Service(doc = "RestEasy REST client")
public class RESTEasyHTTPService implements Lifecycle {
   private static final Log log = LogFactory.getLog(RESTEasyHTTPService.class);
   private ResteasyClient httpClient = null;

   @Property(doc = "The default path on the server for the REST service. Defaults to 'rest'.")
   private String rootPath = "rest";

   @Property(doc = "The username to use on an authenticated server. Defaults to null.")
   private String username;

   @Property(doc = "The password of the username to use on an authenticated server. Defaults to null.")
   private String password;

   @Property(doc = "The content type used for put and get operations. Defaults to application/octet-stream.")
   private String contentType = "application/octet-stream";

   @Property(doc = "Expected cache name. Requests for other caches will fail. Defaults to 'default'.")
   protected String cacheName = "default";

   @Property(doc = "Semicolon-separated list of server addresses.", converter = RESTAddressListConverter.class)
   protected List<InetSocketAddress> servers;

   @Property(doc = "Timeout for socket. Default is 30 seconds.", converter = TimeConverter.class)
   protected long socketTimeout = 30000;

   @Property(doc = "Timeout for connection. Default is 30 seconds.", converter = TimeConverter.class)
   protected long connectionTimeout = 30000;

   @Property(doc = "The size of the connection pool. Default is unlimited.")
   protected int maxConnections = 0;

   @Property(doc = "The number of connections to pool per url. Default is equal to <code>maxConnections</code>.")
   protected int maxConnectionsPerHost = 0;

   // Used to load balance requests across servers
   protected AtomicInteger nextIndex = new AtomicInteger(0);

   @ProvidesTrait
   public RESTEasyHTTPOperations createOperations() {
      return new RESTEasyHTTPOperations(this);
   }

   @ProvidesTrait
   public Lifecycle getLifecycle() {
      return this;
   }

   @Override
   public synchronized void start() {
      if (httpClient != null) {
         log.warn("Service already started");
         return;
      }

      httpClient = new ResteasyClientBuilder().httpEngine(new URLConnectionEngine())
            .establishConnectionTimeout(connectionTimeout, TimeUnit.MILLISECONDS)
            .socketTimeout(socketTimeout, TimeUnit.MILLISECONDS).connectionPoolSize(maxConnections)
            .maxPooledPerRoute(maxConnectionsPerHost).hostnameVerification(HostnameVerificationPolicy.ANY).build();

      if (username != null) {
         BasicAuthentication auth = new BasicAuthentication(username, password);
         httpClient.register(auth);
      }

   }

   public String getContentType() {
      return contentType;
   }

   public String getRootPath() {
      return rootPath;
   }

   public ResteasyClient getHttpClient() {
      return httpClient;
   }

   public String getUsername() {
      return username;
   }

   public String getPassword() {
      return password;
   }

   @Override
   public synchronized void stop() {
      if (httpClient == null) {
         log.warn("Service not started");
         return;
      }
      httpClient.close();
      httpClient = null;
   }

   @Override
   public synchronized boolean isRunning() {
      return httpClient != null;
   }

   /**
    * Distributes HTTP requests among servers.
    * 
    * @return InetSocketAddress of the next server to use.
    */
   public InetSocketAddress nextServer() {
      return servers.get((nextIndex.getAndIncrement() & Integer.MAX_VALUE) % servers.size());
   }

}

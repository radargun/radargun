package org.radargun.http.service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient43Engine;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.KeyValueProperty;
import org.radargun.utils.RESTAddressListConverter;
import org.radargun.utils.TimeConverter;

/**
 * Abstract REST client service
 *
 */
public abstract class AbstractRESTEasyService implements Lifecycle {

   private static final Log log = LogFactory.getLog(AbstractRESTEasyService.class);

   private ResteasyClient httpClient = null;

   @Property(doc = "The username to use on an authenticated server. Defaults to null.")
   private String username;

   @Property(doc = "The password of the username to use on an authenticated server. Defaults to null.")
   private String password;

   @Property(doc = "The content type used for put and get operations. Defaults to application/octet-stream.")
   private String contentType = "application/octet-stream";

   @Property(doc = "Timeout for socket. Default is 30 seconds.", converter = TimeConverter.class)
   private int socketTimeout = 30000;

   @Property(doc = "Timeout for connection. Default is 30 seconds.", converter = TimeConverter.class)
   private long connectionTimeout = 30000;

   @Property(doc = "The size of the connection pool. Default is unlimited.")
   private int maxConnections = 0;

   @Property(doc = "The number of connections to pool per url. Default is equal to <code>maxConnections</code>.")
   private int maxConnectionsPerHost = 0;

   @Property(doc = "Semicolon-separated list of server addresses.", converter = RESTAddressListConverter.class)
   private List<InetSocketAddress> servers;

   @Property(doc = "Http headers for request. Default is null", complexConverter = KeyValueProperty.KeyValuePairListConverter.class)
   private List<KeyValueProperty> httpHeaders;

   @Property(doc = "The HTTP protocol. Default is http")
   private String protocol = "http";

   @Property(doc = "Trust all https certificates. Default is false")
   private boolean trustAll = false;

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

      if (trustAll) {
         HttpsHelper.trustAll();
      }

      PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
      if (maxConnections > 0) {
         cm.setMaxTotal(maxConnections);
      }
      if (maxConnectionsPerHost > 0) {
         cm.setDefaultMaxPerRoute(maxConnectionsPerHost);
      }
      cm.setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(socketTimeout).build());
      CloseableHttpClient closeableHttpClient = HttpClients.custom().setConnectionManager(cm).build();
      ApacheHttpClient43Engine engine = new ApacheHttpClient43Engine(closeableHttpClient);

      ResteasyClientBuilder clientBuilder = new ResteasyClientBuilderImpl();
      clientBuilder.httpEngine(engine);
      clientBuilder.connectTimeout(connectionTimeout, TimeUnit.MILLISECONDS);
      clientBuilder.hostnameVerification(ResteasyClientBuilder.HostnameVerificationPolicy.ANY);
      clientBuilder.register(new ClientRequestFilter() {
         @Override
         public void filter(ClientRequestContext clientRequestContext) throws IOException {
            // Remove default RESTeasy http request headers
            MultivaluedMap<String, Object> clientRequestHeaders = clientRequestContext.getHeaders();
            if (username != null) {
               String token = username + ":" + password;
               String base64Token = Base64.getEncoder().encodeToString(token.getBytes());
               clientRequestHeaders.put("Authorization", Arrays.asList("Basic " + base64Token));
            }
            clientRequestHeaders.put("Accept-Encoding", Collections.emptyList());
            // Add custom headers
            if (httpHeaders != null) {
               httpHeaders.forEach(keyValue ->
                     clientRequestHeaders.put(keyValue.getKey(), Arrays.asList(keyValue.getValue()))
               );
            }
         }
      });
      httpClient = clientBuilder.build();
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

   public String getUsername() {
      return username;
   }

   public String getPassword() {
      return password;
   }

   public String getContentType() {
      return contentType;
   }

   public long getSocketTimeout() {
      return socketTimeout;
   }

   public long getConnectionTimeout() {
      return connectionTimeout;
   }

   public int getMaxConnections() {
      return maxConnections;
   }

   public int getMaxConnectionsPerHost() {
      return maxConnectionsPerHost;
   }

   public List<InetSocketAddress> getServers() {
      return servers;
   }

   public ResteasyClient getHttpClient() {
      return httpClient;
   }

   // URL
   private String buildUrl(InetSocketAddress node, String rootPath, String username, String password) {
      StringBuilder s = new StringBuilder();
      s.append(protocol).append("://");
      if (username != null) {
         try {
            s.append(URLEncoder.encode(username, "UTF-8")).append(":")
               .append(URLEncoder.encode(password, "UTF-8")).append("@");
         } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Could not encode the supplied username and password", e);
         }
      }
      s.append(node.getHostName()).append(":").append(node.getPort()).append("/");
      if (rootPath != null) {
         s.append(rootPath).append("/");
      }
      if (log.isTraceEnabled()) {
         log.trace("buildCacheUrl = " + s.toString());
      }
      return s.toString();
   }

   public String buildCacheUrl(InetSocketAddress nextServer, String rootPath, String username, String password, String cache) {
      StringBuilder s = new StringBuilder(buildUrl(nextServer, rootPath, username, password));
      s.append(cache);
      if (log.isTraceEnabled()) {
         log.trace("buildCacheUrl(String cache) = " + s.toString());
      }
      return s.toString();
   }

   public String buildApplicationUrl(InetSocketAddress pickServer, String contextPath, String username, String password) {
      return buildUrl(pickServer, contextPath, username, password);
   }
}

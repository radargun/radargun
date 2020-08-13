package org.radargun.http.service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;

import org.jboss.resteasy.client.jaxrs.BasicAuthentication;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.URLConnectionEngine;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.KeyValueProperty;
import org.radargun.utils.RESTAddressListConverter;
import org.radargun.utils.TimeConverter;

/**
 * Abstract REST client service using the JAX-RS 2.0 client api
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
   private long socketTimeout = 30000;

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

      httpClient = new ResteasyClientBuilder().httpEngine(new URLConnectionEngine())
         .establishConnectionTimeout(connectionTimeout, TimeUnit.MILLISECONDS)
         .socketTimeout(socketTimeout, TimeUnit.MILLISECONDS).connectionPoolSize(maxConnections)
         .maxPooledPerRoute(maxConnectionsPerHost).hostnameVerification(ResteasyClientBuilder.HostnameVerificationPolicy.ANY)
         .register(new ClientRequestFilter() {
            @Override
            public void filter(ClientRequestContext clientRequestContext) throws IOException {
               // Remove default RESTeasy http request headers
               MultivaluedMap<String, Object> clientRequestHeaders = clientRequestContext.getHeaders();
               clientRequestHeaders.put("Accept-Encoding", Collections.emptyList());
               // Add custom headers
               if (httpHeaders != null) {
                  httpHeaders.forEach(keyValue ->
                     clientRequestHeaders.put(keyValue.getKey(), Arrays.asList(keyValue.getValue()))
                  );
               }
            }
         }).build();

      if (username != null) {
         BasicAuthentication auth = new BasicAuthentication(username, password);
         httpClient.register(auth);
      }

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

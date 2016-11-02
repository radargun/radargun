package org.radargun.http.service;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
import org.radargun.utils.Fuzzy;
import org.radargun.utils.RESTAddressListConverter;
import org.radargun.utils.TimeConverter;

/**
 * @author Martin Gencur
 */
@Service(doc = "RestEasy REST client for general Web applications")
public class RESTEasyService implements Lifecycle {

   private static final Log log = LogFactory.getLog(RESTEasyService.class);
   private ResteasyClient httpClient = null;

   @Property(doc = "The username to use on an authenticated server. Defaults to null.")
   private String username;

   @Property(doc = "The password of the username to use on an authenticated server. Defaults to null.")
   private String password;

   @Property(doc = "The content type used for put and get operations. Defaults to application/octet-stream.")
   private String contentType = "application/octet-stream";

   @Property(doc = "Semicolon-separated list of server addresses.", converter = RESTAddressListConverter.class)
   protected List<InetSocketAddress> servers;

   @Property(doc = "Ratio between the number of connections to individual servers. " +
                   "Servers from the 'servers' list are indexed from 0. When the client " +
                   "is first created it will choose a server to communicate with according " +
                   "to this load balancing setting. The client will keep communicating with " +
                   "this single server until redirected.", converter = Fuzzy.IntegerConverter.class)
   //By default clients only connect to server 0. Example for two servers with equal load: "50%:0,50%:1"
   protected Fuzzy<Integer> serversLoadBalance = Fuzzy.uniform(0);

   @Property(doc = "Timeout for socket. Default is 30 seconds.", converter = TimeConverter.class)
   protected long socketTimeout = 30000;

   @Property(doc = "Timeout for connection. Default is 30 seconds.", converter = TimeConverter.class)
   protected long connectionTimeout = 30000;

   @Property(doc = "The size of the connection pool. Default is unlimited.")
   protected int maxConnections = 0;

   @Property(doc = "The number of connections to pool per url. Default is equal to <code>maxConnections</code>.")
   protected int maxConnectionsPerHost = 0;

   @ProvidesTrait
   public RESTEasyOperations createOperations() {
      return new RESTEasyOperations(this);
   }

   @ProvidesTrait
   public Lifecycle getLifecycle() {
      return this;
   }

   @Override
   public synchronized void start() {
      checkValidLoadBalancingSettings();
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

   private void checkValidLoadBalancingSettings() {
      for (Integer serverIndex: serversLoadBalance.getProbabilityMap().keySet()) {
         if (serverIndex >= servers.size())
            throw new IllegalStateException("Load balancing settings for the REST client include server index " +
               "which is not in the server list: " + serverIndex);
      }
   }

   public String getContentType() {
      return contentType;
   }

   public ResteasyClient getHttpClient() {
      return httpClient;
   }

   public String getUsername() {
      return username;
   }

   public List<InetSocketAddress> getServers() {
      return servers;
   }

   public Fuzzy<Integer> getServersLoadBalance() {
      return serversLoadBalance;
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
}

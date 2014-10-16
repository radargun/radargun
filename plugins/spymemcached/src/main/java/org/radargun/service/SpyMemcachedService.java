package org.radargun.service;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.spy.memcached.DefaultConnectionFactory;
import net.spy.memcached.FailureMode;
import net.spy.memcached.MemcachedClient;
import org.radargun.Service;
import org.radargun.config.Converter;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.TimeConverter;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Service(doc = "SpyMemcached client")
public class SpyMemcachedService implements Lifecycle {
   private static final Log log = LogFactory.getLog(SpyMemcachedService.class);
   private static final int DEFAULT_PORT = 11211;
   private static final String PATTERN_STRING = "(\\[([0-9A-Fa-f:]+)\\]|([^:/?#]*))(?::(\\d*))?";
   private static final Pattern ADDRESS_PATTERN = Pattern.compile(PATTERN_STRING);

   @Property(doc = "Expected cache name. Requests for other caches will fail. Defaults to null.")
   protected String cacheName;

   @Property(doc = "Semicolon-separated list of server addresses.", converter = AddressListConverter.class)
   protected List<InetSocketAddress> servers;

   @Property(doc = "Failure mode for the client. Default is 'Redistribute' (continue with next living node).")
   protected FailureMode failureMode = FailureMode.Redistribute;

   @Property(doc = "Timeout for operation (request will throw exception after this timeout). Default is 15 seconds."
         , converter = TimeConverter.class)
   protected long operationTimeout = 15000;

   protected volatile MemcachedClient memcachedClient;

   @ProvidesTrait
   public SpyMemcachedOperations createOperations() {
      return new SpyMemcachedOperations(this);
   }

   @ProvidesTrait
   public Lifecycle getLifecycle() {
      return this;
   }

   @Override
   public synchronized void start() {
      if (memcachedClient != null) {
         log.warn("Service already started");
         return;
      }
      try {
         memcachedClient = new MemcachedClient(new DefaultConnectionFactory() {
            @Override
            public FailureMode getFailureMode() {
               return failureMode;
            }

            @Override
            public long getOperationTimeout() {
               return operationTimeout;
            }
         }, servers);
      } catch (IOException e) {
         throw new RuntimeException("Failed to start SpyMemcachedService", e);
      }
   }

   @Override
   public synchronized void stop() {
      if (memcachedClient == null) {
         log.warn("Service not started");
         return;
      }
      memcachedClient.shutdown();
      memcachedClient = null;
   }

   @Override
   public synchronized boolean isRunning() {
      return memcachedClient != null;
   }

   private static class AddressListConverter implements Converter<List<InetSocketAddress>> {

      @Override
      public List<InetSocketAddress> convert(String servers, Type type) {
         List<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>();
         for (String server : servers.split(";")) {
            Matcher matcher = ADDRESS_PATTERN.matcher(server.trim());
            if (matcher.matches()) {
               String v6host = matcher.group(2);
               String v4host = matcher.group(3);
               String host = v6host != null ? v6host.trim() : v4host.trim();
               String portString = matcher.group(4);
               int port = portString == null ? DEFAULT_PORT : Integer.parseInt(portString.trim());
               addresses.add(new InetSocketAddress(host, port));
            } else {
               throw new IllegalArgumentException("Cannot parse host:port from " + server);
            }
         }
         return addresses;
      }

      @Override
      public String convertToString(List<InetSocketAddress> value) {
         StringBuilder sb = new StringBuilder();
         for (InetSocketAddress address : value) {
            if (sb.length() != 0) sb.append(';');
            sb.append(address.getHostString());
            InetAddress inetAddr = address.getAddress();
            if (inetAddr != null) {
               sb.append('=').append(inetAddr.getCanonicalHostName());
               sb.append('=').append(Arrays.toString(inetAddr.getAddress()));
            } else {
               sb.append("(not resolved)");
            }
            sb.append(':').append(address.getPort());
         }
         return sb.toString();
      }

      @Override
      public String allowedPattern(Type type) {
         return PATTERN_STRING;
      }
   }

}

package org.radargun.service;

import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.radargun.Service;
import org.radargun.config.Converter;
import org.radargun.config.Property;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Service(doc = "SpyMemcached client")
public class SpyMemcachedService {
   private static final int DEFAULT_PORT = 11211;
   private static final String PATTERN_STRING = "(\\[([0-9A-Fa-f:]+)\\]|([^:/?#]*))(?::(\\d*))?";
   private static final Pattern ADDRESS_PATTERN = Pattern.compile(PATTERN_STRING);

   @Property(doc = "Expected cache name. Requests for other caches will fail. Defaults to null.")
   protected String cacheName;

   @Property(doc = "Semicolon-separated list of server addresses.", converter = AddressListConverter.class)
   protected List<InetSocketAddress> servers;

   @ProvidesTrait
   public SpyMemcachedOperations createOperations() {
      return new SpyMemcachedOperations(this);
   }

   private static class AddressListConverter implements Converter<List<InetSocketAddress>> {

      @Override
      public List<InetSocketAddress> convert(String servers, Type type) {
         List<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>();
         for (String server : servers.split(";")) {
            Matcher matcher = ADDRESS_PATTERN.matcher(server);
            if (matcher.matches()) {
               String v6host = matcher.group(2);
               String v4host = matcher.group(3);
               String host = v6host != null ? v6host : v4host;
               String portString = matcher.group(4);
               int port = portString == null ? DEFAULT_PORT : Integer.parseInt(portString);
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
            sb.append(address.getHostString()).append(':').append(address.getPort());
         }
         return sb.toString();
      }

      @Override
      public String allowedPattern(Type type) {
         return PATTERN_STRING;
      }
   }

}

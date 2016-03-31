package org.radargun.utils;

import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.radargun.config.Converter;

/**
 * Converter that parses a list of <code>host:port</code> entries and generates a list of
 * <code>InetSocketAddress</code> objects
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public abstract class AddressListConverter implements Converter<List<InetSocketAddress>> {
   private static final String PATTERN_STRING = "(\\[([0-9A-Fa-f:]+)\\]|([^:/?#]*))(?::(\\d*))?";
   private static final Pattern ADDRESS_PATTERN = Pattern.compile(PATTERN_STRING);
   private int defaultPort;

   public AddressListConverter(int defaultPort) {
      super();
      this.defaultPort = defaultPort;
   }

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
            int port = portString == null ? defaultPort : Integer.parseInt(portString.trim());
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
         if (sb.length() != 0)
            sb.append(';');
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

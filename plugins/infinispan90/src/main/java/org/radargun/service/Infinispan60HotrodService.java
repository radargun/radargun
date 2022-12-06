package org.radargun.service;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.configuration.ConnectionPoolConfigurationBuilder;
import org.infinispan.client.hotrod.impl.ConfigurationProperties;
import org.infinispan.client.hotrod.marshall.ProtoStreamMarshaller;
import org.infinispan.protostream.MessageMarshaller;
import org.infinispan.protostream.SerializationContext;
import org.radargun.config.Converter;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.network.RadarGunInetAddress;
import org.radargun.traits.ProvidesTrait;
import org.radargun.traits.Queryable;

public abstract class Infinispan60HotrodService extends InfinispanHotrodService {
   protected static final Pattern ADDRESS_PATTERN = Pattern
      .compile("(\\[([0-9A-Fa-f:]+)\\]|([^:/?#]*))(?::(\\d*))?");
   protected static final String CLASS_PATTERN = "([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*";
   protected static final Log log = LogFactory.getLog(Infinispan60HotrodService.class);

   @Property(doc = "Enables query functionality. This requires using the ProtostreamMarshaller," +
      "defining the objects and involves further overhead. Default is false.")
   private boolean enableQuery = false;

   // TODO: use complexConverter when this will be implemented for setup.properties
   @Property(doc = "Classes that should be registered as marshalled. By default, none.",
      converter = RegisteredClassConverter.class)
   protected List<RegisteredClass> classes;

   @Property(doc = "Paths to the .protobin files. Defaul is query/values.protobin")
   protected String[] protofiles = new String[] {"/query/values.protobin"};

   @Property(doc = "Name of the cluster (used for JMX operations). Default is 'default'.")
   protected String clusterName = "default";

   @Property(doc = "JMX port to connect the server. Default is 9999.")
   protected int jmxPort = 9999;

   @Property(doc = "JMX Domain name for components looked up. Default is 'jboss.infinispan'")
   protected String jmxDomain = "jboss.infinispan";

   @Property(doc = "Maximal amount of active connections to single server. Default is unlimited.")
   protected int maxConnectionsServer = -1;

   @Property(doc = "Maximal amount of active connections to all servers. Default is unlimited.")
   protected int maxConnectionsTotal = -1;

   @Property(doc = "Absolute path to the hotrod-client.properties file. Optional.")
   protected String propertiesPath;

   protected ArrayList<String> serverHostnames = new ArrayList<String>();
   protected InfinispanHotrodQueryable queryable;
   protected Configuration configuration;

   @Init
   public void init() {
      ConfigurationBuilder builder = getDefaultHotRodConfig();
      if (propertiesPath != null) {
         Properties p = new Properties();
         try (Reader r = new FileReader(propertiesPath)) {
            p.load(r);
            builder.withProperties(p);
         } catch (IOException e) {
            throw new IllegalStateException("Something went wrong with provided properties file:" + propertiesPath, e);
         }
      }
      afterConfigurationPropertiesLoad(builder);
      configuration = builder.build();
   }

   protected void afterConfigurationPropertiesLoad(ConfigurationBuilder builder) {

   }

   protected ConfigurationBuilder getDefaultHotRodConfig() {
      ConfigurationBuilder builder = new ConfigurationBuilder();
      configureConnectionPool(builder.connectionPool());
      parseServerAddresses().forEach((address) -> builder.addServer().host(address.getHost()).port(address.getPort()));
      createQueryConfiguration(builder);
      return builder;
   }

   protected void configureConnectionPool(ConnectionPoolConfigurationBuilder poolConfigurationBuilder) {
      poolConfigurationBuilder.maxActive(maxConnectionsServer).maxTotal(maxConnectionsTotal);
   }

   protected ConfigurationBuilder createQueryConfiguration(ConfigurationBuilder builder) {
      if (enableQuery) {
         queryable = createQueryable();
         ProtoStreamMarshaller marshaller = new ProtoStreamMarshaller();
         builder.marshaller(marshaller);
         SerializationContext context = marshaller.getSerializationContext();
         queryable.registerProtofilesLocal(context);
         // remote registration has to be delayed until we have running servers
         // register marshallers
         registerMarshallers(context);
      }
      return builder;
   }

   protected List<RadarGunInetAddress> parseServerAddresses() {
      List<RadarGunInetAddress> addresses = new ArrayList<>();
      for (String server : servers.split(";")) {
         Matcher matcher = ADDRESS_PATTERN.matcher(server);
         if (!matcher.matches()) {
            log.error("Could not parse server address from " + server);
            continue;
         }
         String v6host = matcher.group(2);
         String v4host = matcher.group(3);
         String host = v6host != null ? v6host : v4host;
         String portString = matcher.group(4);
         int port = portString == null
            ? ConfigurationProperties.DEFAULT_HOTROD_PORT
            : Integer.parseInt(portString);

         addresses.add(new RadarGunInetAddress(host, port));
         serverHostnames.add(host);
      }
      return addresses;
   }

   protected abstract InfinispanHotrodQueryable createQueryable();

   protected abstract void registerMarshallers(SerializationContext context);

   public abstract Queryable getQueryable();

   @ProvidesTrait
   public Infinispan60HotRodCacheInfo creeateCacheInfo() {
      return new Infinispan60HotRodCacheInfo(this);
   }

   @Override
   public void start() {
      managerNoReturn = new RemoteCacheManager(configuration, true);
      managerForceReturn = new RemoteCacheManager(configuration, true);
      if (queryable != null) {
         queryable.registerProtofilesRemote();
      }
   }

   // TODO: this is prepared for ReflexiveListConverter
   @DefinitionElement(name = "class", doc = "Class that should be registered for protostream.")
   protected static class RegisteredClass<T> {
      @Property(name = "", doc = "Full name of the class that should be registered.", optional = false, converter = ClassLoadConverter.class)
      public Class<T> clazz;

      @Property(doc = "Full name of the class marshaller that should be used. By default, inner class with name 'Marshaller' is used.",
         converter = ClassLoadConverter.class)
      Class<? extends MessageMarshaller<T>> marshaller;

      public RegisteredClass() {
      }

      public RegisteredClass(Class<T> clazz, Class<? extends MessageMarshaller<T>> marshaller) {
         this.clazz = clazz;
         this.marshaller = marshaller;
      }

      public MessageMarshaller<T> getMarshaller() throws IllegalAccessException, InstantiationException {
         if (marshaller != null) {
            if (MessageMarshaller.class.isAssignableFrom(marshaller)) {
               throw new IllegalArgumentException(marshaller.getName() + " does not inherit from MessageMarshaller");
            }
            return marshaller.newInstance();
         }
         for (Class inner : clazz.getClasses()) {
            if (!"Marshaller".equals(inner.getSimpleName())) {
               log.trace(inner.getName() + " is not called Marshaller");
            } else if (!MessageMarshaller.class.isAssignableFrom(inner)) {
               log.trace(inner.getName() + " does not inherit from MessageMarshaller");
            } else if (!Modifier.isStatic(inner.getModifiers())) {
               log.trace(inner.getName() + " is not static class");
            } else {
               return (MessageMarshaller<T>) inner.newInstance();
            }
         }
         throw new IllegalStateException("No marshaller class");
      }
   }

   // TODO: this is not used currently
   private static class ClassLoadConverter implements Converter<Class<?>> {
      @Override
      public Class<?> convert(String string, Type type) {
         try {
            return Class.forName(string);
         } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Cannot instantiate class " + string, e);
         }
      }

      @Override
      public String convertToString(Class<?> value) {
         return value == null ? null : value.getName();
      }

      @Override
      public String allowedPattern(Type type) {
         return CLASS_PATTERN;
      }
   }

   private static class RegisteredClassConverter implements Converter<List<RegisteredClass>> {
      @Override
      public List<RegisteredClass> convert(String string, Type type) {
         String[] parts = string.split(";", 0);
         ArrayList<RegisteredClass> list = new ArrayList<RegisteredClass>(parts.length);
         for (String part : parts) {
            if (part.trim().isEmpty()) continue;
            int colon = part.indexOf(':');
            String className, marshaller = null;
            if (colon < 0) {
               className = part.trim();
            } else {
               className = part.substring(0, colon).trim();
               marshaller = part.substring(colon + 1).trim();
            }
            try {
               list.add(new RegisteredClass(Class.forName(className),
                  marshaller == null ? null : Class.forName(marshaller)));
            } catch (ClassNotFoundException e) {
               throw new IllegalArgumentException("Cannot find class " + part, e);
            }
         }
         return list;
      }

      @Override
      public String convertToString(List<RegisteredClass> values) {
         StringBuilder sb = new StringBuilder();
         boolean first = true;
         if (values != null) {
            for (RegisteredClass rc : values) {
               if (!first) sb.append(", ");
               first = false;
               sb.append(rc.clazz.getName());
               if (rc.marshaller != null) {
                  sb.append(':').append(rc.marshaller.getName());
               }
            }
         }
         return sb.toString();
      }

      @Override
      public String allowedPattern(Type type) {
         return CLASS_PATTERN + "(:" + CLASS_PATTERN + ")?(;\\s*" + CLASS_PATTERN + "(:" + CLASS_PATTERN + ")?)*";
      }
   }

   public RemoteCacheManager getRemoteManager(boolean forceReturn) {
      return forceReturn ? managerForceReturn : managerNoReturn;
   }
}

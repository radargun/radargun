package org.radargun;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

import org.radargun.config.Definition;
import org.radargun.config.InitHelper;
import org.radargun.config.PropertyHelper;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.utils.Utils;

/**
 * Helper class, the only function is instantiating and initializing the service.
 */
public final class ServiceHelper {
   private static final Log log = LogFactory.getLog(ServiceHelper.class);
   private static final String SERVICE_PROPERTY_PREFIX = "service.";

   private static String currentPlugin;
   private static String currentConfigName;
   private static int currentSlaveIndex;

   private ServiceHelper() {}

   public static String getPlugin() {
      return currentPlugin;
   }

   public static String getConfigName() {
      return currentConfigName;
   }

   public static int getSlaveIndex() {
      return currentSlaveIndex;
   }

   /**
    * As we expect only one service at time to be running on one node, this sets current
    * plugin, configuration name and slave index that can be later retrieved, e.g. in some
    * init method (annotated by {@link org.radargun.config.Init}) that would not be able
    * to retrieve this information in another way.
    *
    * @param plugin
    * @param configName
    * @param slaveIndex
    */
   public static void setServiceContext(String plugin, String configName, int slaveIndex) {
      currentPlugin = plugin;
      currentConfigName = configName;
      currentSlaveIndex = slaveIndex;
   }

   /**
    * Instantiates the service, looking up file plugin.properties for the service name
    * (in form of service./service name/ /service class/
    * The service class must have public no-arg constructor.
    * Then, sets up all properties declared on the service (and its superclasses).
    * Finally calls any methods of the class annotated by {@link org.radargun.config.Init @Init}.
    *
    * Don't forget to call {@link #setServiceContext(String, String, int)} before calling this method.
    */
   public static Object createService(String plugin, String service,
                                      Map<String, Definition> properties, Map<String, String> extras) {
      String serviceClassName = Utils.getPluginProperty(plugin, SERVICE_PROPERTY_PREFIX + service);
      if (serviceClassName == null) {
         throw new IllegalStateException(String.format("Cannot find service %s for plugin %s", service, plugin));
      }
      Class<?> serviceClazz = null;
      try {
         serviceClazz = Class.forName(serviceClassName);
      } catch (Throwable t) {
         throw new IllegalArgumentException("Cannot load class " + serviceClassName + " from plugin " + plugin, t);
      }
      boolean isService = false;
      for (Annotation annotation : serviceClazz.getDeclaredAnnotations()) {
         if (annotation.annotationType().equals(Service.class)) {
            isService = true;
         }
      }
      if (!isService) {
         throw new IllegalArgumentException("Class " + serviceClassName + " is not declared as a Service");
      }

      Object instance;
      try {
         instance = serviceClazz.newInstance();
      } catch (Exception e) {
         throw new RuntimeException("Cannot instantiate service " + serviceClassName, e);
      }

      // The properties are evaluated only on the slave, using the extras (such as ${slave.index} etc...)
      Map<String, String> backupExtras = new HashMap<String, String>();
      for (Map.Entry<String, String> extra : extras.entrySet()) {
         backupExtras.put(extra.getKey(), System.getProperty(extra.getKey()));
         System.setProperty(extra.getKey(), extra.getValue());
      }
      PropertyHelper.setPropertiesFromDefinitions(instance, properties, false, true);
      for (Map.Entry<String, String> backup : backupExtras.entrySet()) {
         System.setProperty(backup.getKey(), backup.getValue() == null ? "" : backup.getValue());
      }
      InitHelper.init(instance);
      return instance;
   }

   public static Map<String, Class<?>> loadServices(String plugin) {
      Map<String, Class<?>> services = new HashMap<>();
      java.util.Properties properties = Utils.getPluginProperties(plugin);
      for (String property : properties.stringPropertyNames()) {
         if (!property.startsWith(SERVICE_PROPERTY_PREFIX)) continue;
         String name = property.substring(property.indexOf('.') + 1);
         String clazzName = properties.getProperty(property);
         try {
            Class<?> clazz = Class.forName(clazzName);
            services.put(name, clazz);
         } catch (ClassNotFoundException e) {
            log.warn("Failed to load class " + clazzName, e);
         }
      }
      return services;
   }
}

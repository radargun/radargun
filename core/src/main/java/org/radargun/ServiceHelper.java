package org.radargun;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

import org.radargun.config.InitHelper;
import org.radargun.config.PropertyHelper;
import org.radargun.utils.Utils;

/**
 * Helper class, the only function is instantiating and initializing the service.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class ServiceHelper {
   /**
    * Instantiates the service, looking up file plugin.properties for the service name
    * (in form of service./service name/ /service class/
    * The service class must have public no-arg constructor.
    * Then, sets up all properties declared on the service (and its superclasses).
    * Finally calls any methods of the class annotated by {@link org.radargun.config.Init @Init}.
    */
   public static Object createService(ClassLoader classLoader, String plugin, String service, String configName, String configFile, Map<String, String> properties) {
      Thread.currentThread().setContextClassLoader(classLoader);

      String serviceClassName = Utils.getServiceProperty(plugin, "service." + service);
      Class<?> serviceClazz = null;
      try {
         serviceClazz = classLoader.loadClass(serviceClassName);
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

      Map<String, String> configProperties = new HashMap<String, String>(properties);
      configProperties.put(Service.PROP_CONFIG_NAME, configName);
      configProperties.put(Service.PROP_PLUGIN, plugin);
      if (configFile != null) {
         configProperties.put(Service.PROP_FILE, configFile);
      }
      PropertyHelper.setProperties(instance, configProperties, true, true);
      InitHelper.init(instance);
      return instance;
   }
}

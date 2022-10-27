package org.radargun.service;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.infinispan.AdvancedCache;
import org.infinispan.container.DataContainer;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.factories.ComponentRegistry;
import org.radargun.logging.Level;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Debugable;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanDebugable implements Debugable {

   protected final Log log = LogFactory.getLog(getClass());
   protected final boolean trace = log.isTraceEnabled();

   protected final InfinispanEmbeddedService service;

   public InfinispanDebugable(InfinispanEmbeddedService service) {
      this.service = service;
   }

   @Override
   public Debugable.Cache getCache(String cacheName) {
      return new Cache(service.getCache(cacheName).getAdvancedCache());
   }

   protected String[] getDebugKeyPackages() {
      return new String[] {"org.infinispan", "org.jgroups"};
   }

   protected String[] getDebugKeyClassesTraceFix() {
      return new String[] {"org.infinispan.container.EntryFactoryImpl"};
   }

   private void setTraceField(ComponentRegistry registry, String clazzName, boolean value) {
      /* Use -XX:+UnlockDiagnosticVMOptions -XX:CompileCommand=exclude,my/package/MyClass,myMethod */
      try {
         Class<?> clazz = Class.forName(clazzName);
         Field traceField = clazz.getDeclaredField("trace");
         traceField.setAccessible(true);
         Field modifiers = Field.class.getDeclaredField("modifiers");
         modifiers.setAccessible(true);
         modifiers.setInt(traceField, traceField.getModifiers() & ~Modifier.FINAL);
         if (Modifier.isStatic(traceField.getModifiers())) {
            traceField.setBoolean(null, value);
         } else {
            // if this is instance-variable, try to get instance from registry
            Object component = null;
            component = registry.getComponent(clazz);
            if (component == null) {
               Class<?>[] ifaces = clazz.getInterfaces();
               if (ifaces.length > 0) {
                  component = registry.getComponent(ifaces[0]);
               }
            }
            if (component == null) {
               log.warn("No instance can be found for " + clazzName);
            } else if (!clazz.isAssignableFrom(component.getClass())) {
               log.warn("The actual instance is not " + clazzName + ", it is " + component.getClass().getName());
            } else {
               traceField.setBoolean(component, value);
            }
         }
      } catch (ClassNotFoundException e) {
         log.warn("Failed to set " + clazzName + "trace=" + value + " (cannot load class)", e);
      } catch (NoSuchFieldException e) {
         log.warn("Failed to set " + clazzName + "trace=" + value + " (cannot find field)", e);
      } catch (SecurityException e) {
         log.warn("Failed to set " + clazzName + "trace=" + value + " (cannot access field)", e);
      } catch (IllegalAccessException e) {
         log.warn("Failed to set " + clazzName + "trace=" + value + " (cannot write field)", e);
      } catch (Throwable e) {
         log.warn("Failed to set " + clazzName + "trace=" + value, e);
      }
   }

   protected class Cache<K> implements Debugable.Cache<K> {
      protected final AdvancedCache cache;

      public Cache(AdvancedCache cache) {
         this.cache = cache;
      }

      @Override
      public void debugKey(K key) {
         try {
            log.debug(service.getKeyInfo(cache, key));
            List<Level> levels = new ArrayList<Level>();
            String[] debugPackages = getDebugKeyPackages();
            ComponentRegistry componentRegistry = cache.getComponentRegistry();
            try {
               for (String pkg : debugPackages) {
                  Log logger = LogFactory.getLog(pkg);
                  levels.add(logger.getLevel());
                  logger.setLevel(Level.TRACE);
               }
               for (String clazz : getDebugKeyClassesTraceFix()) {
                  setTraceField(componentRegistry, clazz, true);
               }
               cache.get(key);
            } finally {
               int i = 0;
               for (Level l : levels) {
                  LogFactory.getLog(debugPackages[i]).setLevel(l);
                  ++i;
               }
               for (String clazz : getDebugKeyClassesTraceFix()) {
                  setTraceField(componentRegistry, clazz, false);
               }
            }
         } catch (Throwable t) {
            log.error("Debugging key " + key + " failed", t);
         }
      }

      @Override
      public void debugInfo() {
         DistributionManager dm = cache.getDistributionManager();
         DataContainer container = cache.getDataContainer();
         StringBuilder sb = new StringBuilder(256);
         sb.append("Debug info for ").append(cache.getName()).append(": joinComplete=").append(dm.isJoinComplete());
         sb.append(", rehashInProgress=").append(dm.isRehashInProgress());
         sb.append(", numEntries=").append(container.size());
         sb.append(service.getCHInfo(dm));
         log.debug(sb.toString());
      }
   }
}

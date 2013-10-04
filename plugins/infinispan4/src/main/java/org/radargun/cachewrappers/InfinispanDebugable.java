package org.radargun.cachewrappers;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.infinispan.container.DataContainer;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.factories.ComponentRegistry;
import org.radargun.features.Debugable;

/**
 * // TODO: Document this
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanDebugable implements Debugable {

   protected final Log log = LogFactory.getLog(getClass());
   protected final boolean trace = log.isTraceEnabled();

   protected final InfinispanWrapper wrapper;

   public InfinispanDebugable(InfinispanWrapper wrapper) {
      this.wrapper = wrapper;
   }

   protected String[] getDebugKeyPackages() {
      return new String[] { "org.infinispan", "org.jgroups" };
   }

   protected String[] getDebugKeyClassesTraceFix() {
      return new String[] { "org.infinispan.container.EntryFactoryImpl" };
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

   @Override
   public void debugKey(String bucket, Object key) {
      log.debug(wrapper.getKeyInfo(bucket, key));
      List<Level> levels = new ArrayList<Level>();
      String[] debugPackages = getDebugKeyPackages();
      ComponentRegistry componentRegistry = wrapper.getCache(bucket).getAdvancedCache().getComponentRegistry();
      try {
         for (String pkg : debugPackages) {
            Logger logger = Logger.getLogger(pkg);
            levels.add(logger.getLevel());
            logger.setLevel(Level.TRACE);
         }
         for (String clazz : getDebugKeyClassesTraceFix()) {
            setTraceField(componentRegistry, clazz, true);
         }
         wrapper.getCache(bucket).get(key);
      } finally {
         int i = 0;
         for (Level l : levels) {
            Logger.getLogger(debugPackages[i]).setLevel(l);
            ++i;
         }
         for (String clazz : getDebugKeyClassesTraceFix()) {
            setTraceField(componentRegistry, clazz, false);
         }
      }
   }

   @Override
   public void debugInfo(String bucket) {
      DistributionManager dm = wrapper.getCache(bucket).getAdvancedCache().getDistributionManager();
      DataContainer container = wrapper.getCache(bucket).getAdvancedCache().getDataContainer();
      StringBuilder sb = new StringBuilder(256);
      sb.append("Debug info for ").append(bucket).append(": joinComplete=").append(dm.isJoinComplete());
      sb.append(", rehashInProgress=").append(dm.isRehashInProgress());
      sb.append(", numEntries=").append(container.size());
      sb.append(wrapper.getCHInfo(dm));
      log.debug(sb.toString());
   }
}

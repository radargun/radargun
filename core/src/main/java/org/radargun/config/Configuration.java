package org.radargun.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Describes one configuration of one product-config unit
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Configuration implements Serializable {
   public static final String DEFAULT_SERVICE = "default";

   public final String name;

   private List<Setup> setups = new ArrayList<>();

   public Configuration(String name) {
      if (name == null) throw new NullPointerException("Configuration name cannot be null");
      this.name = name;
   }

   public String getName() {
      return name;
   }

   public Setup addSetup(String base, String group, String plugin, String service, Map<String, Definition> propertyDefinitions, Map<String, Definition> vmArgs, Map<String, Definition> envs, boolean lazyInit) {
      for (Setup s : setups) {
         if (s.group.equals(group)) {
            throw new IllegalArgumentException("Setup for group '" + group + "' already set!");
         }
      }
      Setup setup = new Setup(base, group, plugin, service, propertyDefinitions, vmArgs, envs, lazyInit);
      setups.add(setup);
      return setup;
   }

   public void addSetup(Setup setup) {
      setups.add(setup);
   }

   public List<Setup> getSetups() {
      return Collections.unmodifiableList(setups);
   }

   public Setup getSetup(String groupName) {
      for (Setup s : setups) {
         if (s.group.equals(groupName)) {
            return s;
         }
      }
      throw new IllegalArgumentException("No setup for group '" + groupName + "'");
   }

   public static class SetupBase implements Serializable {
      protected final String base;
      protected final Map<String, Definition> properties;
      protected final Map<String, Definition> vmArgs;
      protected final Map<String, Definition> envs;

      public SetupBase(String base, Map<String, Definition> vmArgs, Map<String, Definition> properties, Map<String, Definition> envs) {
         this.base = base;
         this.vmArgs = vmArgs;
         this.properties = properties;
         this.envs = envs;
      }

      public Map<String, Definition> getProperties() {
         return Collections.unmodifiableMap(properties);
      }

      public Map<String, Definition> getVmArgs() {
         return vmArgs;
      }

      public Map<String, Definition> getEnvironment() {
         return envs;
      }
   }

   public class Setup extends SetupBase {
      public final String group;
      public final String plugin;
      public final String service;
      public boolean lazyInit;

      public Setup(String base, String group, String plugin, String service, Map<String, Definition> properties, Map<String, Definition> vmArgs, Map<String, Definition> envs, boolean lazyInit) {
         super(base, vmArgs, properties, envs);
         this.plugin = plugin;
         this.service = service;
         this.group = group;
         this.lazyInit = lazyInit;
      }

      public Configuration getConfiguration() {
         return Configuration.this;
      }

      public Setup applyTemplates(Map<String, SetupBase> templates) {
         if (base == null) {
            return this;
         }
         List<SetupBase> lineage = new ArrayList<>();
         lineage.add(this);
         SetupBase origin = this;
         do {
            SetupBase template = templates.get(origin.base);
            if (template == null) {
               throw new IllegalArgumentException("Template '" + origin.base + "' does not exist!");
            }
            lineage.add(template);
            origin = template;
         } while (origin.base != null);

         Map<String, Definition> newProperties = merge(lineage, setupBase -> setupBase.properties);
         Map<String, Definition> newVmArgs = merge(lineage, setupBase -> setupBase.vmArgs);
         Map<String, Definition> newEnvs = merge(lineage, setupBase -> setupBase.envs);
         return new Setup(null, group, plugin, service, newProperties, newVmArgs, newEnvs, lazyInit);
      }
   }

   private static Map<String, Definition> merge(List<SetupBase> lineage, Function<SetupBase, Map<String, Definition>> selector) {
      Map<String, Definition> definitions = new HashMap<>();
      for (int i = lineage.size() - 1; i >= 0; --i) {
         selector.apply(lineage.get(i)).forEach((name, definition) -> {
            Definition prev = definitions.get(name);
            definitions.put(name, prev == null ? definition : prev.apply(definition));
         });
      }
      return definitions;
   }
}

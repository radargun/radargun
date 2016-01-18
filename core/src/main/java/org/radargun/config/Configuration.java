package org.radargun.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Describes one configuration of one product-config unit
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Configuration implements Serializable {
   public static final String DEFAULT_SERVICE = "default";

   public final String name;

   private List<Setup> setups = new ArrayList<Setup>();

   public Configuration(String name) {
      if (name == null) throw new NullPointerException("Configuration name cannot be null");
      this.name = name;
   }

   public Setup addSetup(String group, String plugin, String service, Map<String, Definition> propertyDefinitions, Map<String, Definition> vmArgs, Map<String, Definition> envs) {
      for (Setup s : setups) {
         if (s.group.equals(group)) {
            throw new IllegalArgumentException("Setup for group '" + group + "' already set!");
         }
      }
      Setup setup = new Setup(group, plugin, service, propertyDefinitions, vmArgs, envs);
      setups.add(setup);
      return setup;
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

   public class Setup implements Serializable {
      public final String group;
      public final String plugin;
      public final String service;
      private final Map<String, Definition> properties;
      private final Map<String, Definition> vmArgs;
      private final Map<String, Definition> envs;

      public Setup(String group, String plugin, String service, Map<String, Definition> properties, Map<String, Definition> vmArgs, Map<String, Definition> envs) {
         this.plugin = plugin;
         this.service = service;
         this.group = group;
         this.properties = properties;
         this.vmArgs = vmArgs;
         this.envs = envs;
      }

      public Map<String, Definition> getProperties() {
         return Collections.unmodifiableMap(properties);
      }

      public Configuration getConfiguration() {
         return Configuration.this;
      }

      public Map<String, Definition> getVmArgs() {
         return vmArgs;
      }

      public Map<String, Definition> getEnvironment() {
         return envs;
      }
   }
}

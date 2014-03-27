package org.radargun.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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

   public Setup addSetup(String plugin, String file, String service, String group) {
      for (Setup s : setups) {
         if (s.group.equals(group)) {
            throw new IllegalArgumentException("Setup for group '" + group + "' already set!");
         }
      }
      Setup setup = new Setup(plugin, file, service, group);
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
      public final String plugin;
      public final String group;
      public final String file;
      public final String service;

      private Map<String, String> properties = new HashMap<String, String>();

      public Setup(String plugin, String file, String service, String group) {
         this.plugin = plugin;
         this.group = group;
         this.file = file;
         this.service = service;
      }

      public void addProperty(String name, String value) {
         if (properties.get(name) != null) {
            throw new IllegalArgumentException("Property '" + name + "' already set!");
         }
         properties.put(name, value);
      }

      public Map<String, String> getProperties() {
         return Collections.unmodifiableMap(properties);
      }

      public Configuration getConfiguration() {
         return Configuration.this;
      }
   }
}

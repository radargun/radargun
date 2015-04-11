JSR-107 (JCache) RadarGun Plug-in
--------------------------------------

This plugin simplifies integration of JCache (JSR-107) implementations with RadarGun.

In order to use the plugin, add dependency on this module to chosen plugin.

   <dependency>
      <groupId>org.radargun</groupId>
      <artifactId>plugin-jcache</artifactId>
      <version>${project.version}</version>
   </dependency>

Moreover, make sure JCacheService reference is included in plugin.properties file of the plugin.

   service.jcache org.radargun.service.JCacheService

See benchmark-jcache.xml example config for sample usage.

---
---

Writing custom plugins
----------------------
### Note: this document is related to RadarGun 1.x

For RadarGun 2.0, the **CacheWrapper** was renamed to **Service** and the actual SPI was split into **Traits**. See [Design Documentation]({{page.path_to_root}}architecture/design_documentation.html) for more information.

----------------------

### Context

RadarGun is meant to be a generic benchmarking mechanism in which both the benchmark (i.e. the data access pattern used) and the product that needs to be benchmarked are pluggable. This page describes the way in which new products (i.e. *plugins*, in RadarGun parlance) can be added to the framework.

### Directory structure

1. The first thing to do is to get the source code from git (a plugin can also be built based on binaries, but this is the simplest way to do it). In order to do that, refer to the *Download source code* section on the [Building Binaries]({{page.path_to_root}}getting_started/building_binaries.html)
2. Add new product plugin directory. A new directory should be added under the `${RADARGUN_HOME}/plugins` directory. The naming convention used is `<product-name><major-version>`, e.g., `infinispan4`, `jbosscache3` etc. All the plugin-related code and configuration will be added to this directory. **Important**: this directory name will identify the product and will be used in configuration files. E.g. in **BROKEN LINK** [this benchmark.xml file](https://github.com/radargun/radargun/blob/master/framework/src/main/resources/benchmark.xml) `jbosscache3` and `infinispan4` correspond to existing plugin directories `${RADARGUN_HOME}/plugins/jbosscache3` and `${RADARGUN_HOME}/plugins/infinispan4`, respectively.
3. Add maven support. It's going to be maven that will do the building and assembly, so maven configuration files will be required. Add a `pom.xml` file in the root of the newly created module. As an example, refer to the `infinispan4` plugin's `pom.xml` [file](https://github.com/radargun/radargun/blob/master/plugins/infinispan4/pom.xml)
4. Implement the `CacheWrapper` interface. This is an abstraction of a distributed cache, and RadarGun is only aware of this abstraction. Follow the javadocs for more details of the API.  Useful help might come from existing implementations as well. This new implementation should be placed under `${RADARGUN_ROOT}/plugins/<new-plugin>/src/main/java`. As an example, if the plugin class is `org.radargun.ProductXyzWrapper`, then the file location relative to the RadarGun root is: `${RADARGUN_ROOT}/plugins/<new-plugin>/src/main/java/org/radargun/ProductXyzWrapper.java`
5. Specify the plugin implementation. The following file must exist: `${RADARGUN_ROOT}/plugins/<new-plugin>/src/main/resources/cacheprovider.properties`. This file should contain following entry (replace `org.radargun.ProductXyzWrapper` with the appropriate class name):

    org.radargun.wrapper org.radargun.ProductXyzWrapper
    
6. Other resources needed by the cache wrapper implementation. Any other such resources should be placed under `${RADARGUN_ROOT}/plugins/<new-plugin>/src/main/resources`. That directory will be in the classpath at runtime.

### Rebuilding the distribution

After all the above steps are done, you'll have to re-build a distribution to include the new plugin. First thing you'll have to enable the plugin in `${RADARGUN_ROOT}/pom.xml`, by adding it in the module section:

    <modules>
      <module>framework</module>
      <!-- add the plugin bellow, as an module element-->
      <module>plugins/infinispan4</module>
      <module>plugins/jbosscache2</module>
      <module>plugins/jbosscache3</module>
      <module>plugins/ehcache16</module>
      <module>plugins/ehcache17</module>
      <module>plugins/terracotta3</module>
    </modules>

After that follow the stept described in the `Compile and build distribution` section of [Building Binaries]({{page.path_to_root}}getting_started/building_binaries.html)

### Contribute your changes!

If it is not for a proprietary clustering solution, we request you to contribute your new plugin back to the RadarGun project, via a pull request on GitHub.


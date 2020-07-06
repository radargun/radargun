---
---

Configurations
--------------

Configurations is the element of benchmark configuration file which lists all configurations that are to be ran on [clusters](./clusters.html).  
Each configuration has its own `config` element containing as many `setup` elements as is necessary to cover all specified groups of any cluster. 

**Setup** element attributes
> plugin (**required**) -	specifies which plugin will be ran on the group (or cluster if no groups are used)  
> group (**optional**) -	specifies which group the setup is for, required if the groups are used in clusters configuration  


The plugins themselves define their own configuration format in their separate namespaces, but there are two elements defined by core RadarGun. These values apply only to worker VM, if plugin spawns another VM the handling of arguments and varibles for that VM depends on plugin implementation (they are usually defined in separate element).

* **Note:** These values are not applied to worker instances you start, workers get automatically restarted after the configuration is distributed to them by main.

**Setup** core elements
> vm-args (**optional**) -	defines VM arguments (there is a number of child elements, please refer to the schema file)  
> environment (**optional**) -	defines VM environment variables in key-value pairs  
  

Following examples fully apply only to their respective plugins. 

#### Basic configuration

{% highlight xml %}
    <configurations>
      <config name="Infinispan 5.2 - distributed">
        <setup plugin="infinispan52" >
          <embedded xmlns="urn:radargun:plugins:infinispan52:3.0" file="dist-sync.xml"/>
        </setup>
      </config>
    </configurations>
{% endhighlight %}

This configurations element contains single config which will initialize `infinispan52` plugin on all workers (as there are no group definitions) and have it run in embedded mode (node inside worker VM) using "dist-sync.xml" file as configuration source.

* The method used to obtain plugin configuration files (where applies) depends entirelly on plugin implementation, there is no central distribution so the user has to make file available to plugin otherwise (copy to worker machine/place on the shared drive/...)
* Among other places, most plugins tend to look for the files inside their own folder in `conf` subfolder, placing it there is a safe bet

#### Not entirely basic configuration

{% highlight xml %}
    <configurations>
      <config name="Infinispan 7.0 - distributed">
        <setup plugin="infinispan70" group="g1">
          <embedded xmlns="urn:radargun:plugins:infinispan70:3.0"
            file="dist-no-tx_site1_70.xml" cache="testCacheSite1" />
        </setup>
        <setup plugin="infinispan70" group="g2">
          <embedded xmlns="urn:radargun:plugins:infinispan70:3.0"
            file="dist-no-tx_site2_70.xml" cache="testCacheSite2" />
        </setup>
      </config>
      <config name="Infinispan 9.0 - distributed">
        <setup plugin="infinispan90" group="g1">
          <embedded xmlns="urn:radargun:plugins:infinispan70:3.0"
            file="dist-no-tx_site1_90.xml" cache="testCacheSite1" />
        </setup>
        <setup plugin="infinispan90" group="g2">
          <embedded xmlns="urn:radargun:plugins:infinispan70:3.0"
            file="dist-no-tx_site2_90.xml" cache="testCacheSite2" />
        </setup>
      </config>
    </configurations>
{% endhighlight %}

This configuration element contains two configs, each with two setups for different groups (groups have to be defined in [clusters](./clusters.html) element). The first config will run `infinispan70` plugin, the second config will run `infinispan90` plugin, with groups having distinct configuration files and cache configuration.

#### Complex configuration

{% highlight xml %}
    <configurations>
      <config name="ISPN9 - hotrod">
        <setup group="server" plugin="infinispan90">
          <server xmlns="urn:radargun:plugins:infinispan90:3.0" file="streaming.xml"
            jmx-domain="jboss.datagrid-infinispan" start-timeout="120000" cache-manager-name="clustered">
            <args>
              -Djava.net.preferIPv4Stack=true
              -Djboss.node.name=node${worker.index}
              -Djboss.socket.binding.port-offset=${worker.index}00
            </args>
            <home>${env.RG_WORK}/worker${worker.index}</home>
            <server-zip>${env.ISPN_ZIP_PATH}/infinispan-server-9.0.0-SNAPSHOT.zip</server-zip>
            <env>JAVA_OPTS=-server -Xms2g -Xmx4g
              -XX:MaxPermSize=4G
              -verbose:gc
            </env>
          </server>
        </setup>
        <setup group="client" plugin="infinispan90">
          <hotrod xmlns="urn:radargun:plugins:infinispan90:3.0">
            <servers>127.0.0.1:11222,127.0.0.1:11322</servers>
          </hotrod>
          <vm-args>
            <memory max="1G" />
          </vm-args>
        </setup>
      </config>
    </configurations>
{% endhighlight %}

This configuration element has only one config with two defined groups, `server` and `client`. This configuration takes advantage of [properties](./properties.html).

**Server** group will run `infinispan90` plugin in server mode - it will start Infinispan server as separate process and manage it.  
Arguments will hame following effect:
* *home* - path to Infinispan server installation
* *server-zip* - plugin is to lookup Infinispan release zip file on specified path and unpack it into *home*, useful to ensure clean installation for each run
  * ISPN_ZIP_PATH is assumed to be defined as environment [property](./properties.html)
* *env* - environment variables that will be set for server VM
* *args* - VM arguments that will be given to server
  * jboss.node.name - specifies a server node name
  * jboss.socket.binding.port-offset - specifies offset for server port binding, making sure servers do not compete for same ports on localhost
* *default-server-port* - Infinispan single port which will be used for Hotrod, Rest and other operations. Non-WildFly server will be used if this parameter is supplied
* *jgroups-config* - If set, it will replace the default <jgroups> configuration
* *cache-container-config* - If set, it will replace the default <cache-container> configuration
* *interfaces-config* - If set, it will replace the default <interfaces> configuration
* *socket-bindings-config* - If set, it will replace the default <socket-bindings> config
* *endpoints-config* - If set, it will replace the default <endpoints> config
* *security-config* - If set, it will replace the default <security> config

**Client** group will run `infinispan90` plugin in HotRod mode - a java client library over specialized protocol.  
Arguments will have following effect:
* *vm-args* - worker VM will be limited to maximum if 1 Gigabyte of memory
* *servers* - HotRod client will connect to ISPN servers on specified addresses/ports. This configuration assumes only two servers.

*Notes*: Notice the difference between *args* and *vm-args* elements, former is used as configuration for the server, while the latter is used as configuration for the worker instance.

#### Configuration templates

Sometimes when comparing very similar configurations with complex setup these configurations differ by only few details.
Such repetition is error-prone, and therefore RadarGun supports configuration templates:

{% highlight xml %}
    <configurations>
      <template name="common">
        <vm-args>
           <!-- long list of JVM arguments -->
        </vm-args>
        <default xmlns="urn:radargun:plugins:infinispan80:3.0">
          <!-- another complex configuration -->
        </default>
      </template>
      <template name="a" base="common">
        <default xmlns="urn:radargun:plugins:infinispan80:3.0">
          <!-- change something in here -->
        </default>
      </template>
      <template name="b" base="common">
        <default xmlns="urn:radargun:plugins:infinispan80:3.0">
          <!-- change something else -->
        </default>
      </template>
      <config name="8.0 A">
        <setup plugin="infinispan80" base="a" />
      </config>
      <config name="8.0 B">
        <setup plugin="infinispan80" base="b" />
      </config>
      <config name="9.0 A">
        <setup plugin="infinispan90" base="a" />
      </config>
      <config name="9.0 B">
        <setup plugin="infinispan90" base="b" />
      </config>
    </configurations>
{% endhighlight %}

`template` here works as a standalone template for the `setup` element. You can see that it is possible to chain templates to form a hierarchy, too.
The configuration that's later in the hierarchy can override any configuration from the parent template; this has some limitations, though, such as not being able to append one element to a list (e.g. the `vm-args`) - you need to override the whole list.
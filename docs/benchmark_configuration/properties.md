---
---

Properties and evaluations
--------------------------

### Properties

There are several properties available for configuration file to use. All properties are accessed by using `$`, a dollar sign, and providing property name inside braces - `${PROPERTY_NAME}`. Default value for a property can be set by adding the `:` colon and default value between property name and the last brace -  `${PROPERTY_NAME:DEFAULT_VALUE}`.
  
Most properties are resolved during execution on slave (as opposed to initial parsing on master) and many of them refer to current status on that slave as per their individual comments below. 
  
Custom properties can also be specified trough the `define` stage in the [scenario](./scenario.html).

#### Properties example

{% highlight xml %}
    <setup group="server" plugin="infinispan80" >
      <server xmlns="urn:radargun:plugins:infinispan80:3.0" file="${env.ISPN_CONFIG:server.xml}" jmx-domain="jboss.datagrid-infinispan">
        <home>${ISPN_HOME}</home>
      </server>
    </setup>
{% endhighlight %}

This [configuration](./configurations.html) setup element will use `ISPN_CONFIG` environment variable as path to config file or default to "server.xml" if not available. It will also use `ISPN_HOME` system property as path to Infinispan home folder with no default value.
  
Following properties are available:
* **PROPERTY_NAME**		- system properties
* **env.PROPERTY_NAME**		- environment variables
* **random.TYPE**		- random value of specified type (int/long/double/boolean)
* **cluster.size**		- size of the current cluster
* **cluster.maxSize**		- maximum size of cluster for the benchmark
* **group.GROUP_NAME.size**	- size of a specific group in current cluster
* **group.name**		- name of the current group
* **group.size**		- size of the current group
* **plugin.name**		- name of current plugin
* **config.name**		- name of the current configuration
* **slave.index**		- index of current slave (slaves are indexed from 0)
* **process.id**		- ID of the slave process
* **repeat.counter**		- internal itteration counter of enclosing repeat element (only in case there is a only one and name is not set)
* **repeat.REPEAT_NAME.counter**	- internal itteration counter of named enclosing repeat element

### Evaluations

It is possible for configuration parser to evaluate expressions. The evaluation is triggered by using `#`, hashtag or octothorpe sign, the expression itself can contain properties and has to be enclosed in braces.  
  
Following operations are available:
* `(` and `)`	- parentheses
* `+`, `-`,  `*`, `/`, `%`, `^`	- arithmentic operations
* `floor`, `ceil` - floor and ceiling operations

Following list opeartions are availabe:
* `..`	- range generation
* `,`	- addition to list
* `max`, `min`	- maximum and minimul value from the list
* `LIST.get(INDEX)`	- retrieve value from specific INDEX in the LIST
* `gcd`	- will calculate greates common divisor for the list values

Following characters can also be evaluated:
* ` `	- a space (there is one in there, promise)
* `\t`	- a tabulator
* `\n`	- e new line
* `\r`	- a carriage return


#### Simple expressions

    #{2+2}

This expression will add 2 and 2.

#### Expression

    #{(4*5)/2}

This can also be done.

#### Complex expression

    #{gcd(${cluster.size}+1, ${cluster.maxSize})}

This expression will retrieve greatest common divisor between maximum cluster size and current cluster size + 1.

### Notes
* Both properties and evaluations can be nested into each other as many times as neccesary (within reason)
* Properties and evaluations can be used both as element attributes `<tag attribute="${property}"/>` and element values `<tag>${property}</tag>`
* Properties and evalueations can be mixed with static values `<tag attribute="Iteration ${repeat.counter}">`
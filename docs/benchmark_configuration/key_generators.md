---
---

Key generators
--------------

Key generators generate key in the specified form based on input from [key selector](./key_selectors.html). There are two main purposes for picking specific key generator, to provide target application with key in appropriate format or to provide it with key in specific format in order to test behaviour for that specific set of keys.
  
Where applicable default key generator can be overriden by placing following element into stages core element:

{% highlight xml %}
    <key-generator>
      <GENERATOR_ELEMENT_NAME />
    </key-generator>
{% endhighlight %}

#### Generators 

|**Generator name**		| Generator element name|Description														|
|-------------------------------|-----------------------|-----------------------------------------------------------------------------------------------------------------------|
|**ByteArrayKeyGenerator**	|byte-array-key		|Generates byte-array keys												|
|**CargoKeyGenerator**		|cargo			|Generates key objects with the 8-byte index and random byte-array of configurable length (identical for all keys)	|
|**CustomKeyGenerator**		|custom			|Creates keys of specified class, using single long arg constructor							|
|**ObjectKeyGenerator**		|object			|Generates externalizable keys wrapping long identifier of the key							|
|**PluginSpecificKeyGenerator**	|plugin-specific	|Wraps key generator that is specific to current plugin									|
|**StringKeyGenerator**		|string			|Generates strings with configurable [format](https://docs.oracle.com/javase/7/docs/api/java/util/Formatter.html)	|
|**TimestampKeyGenerator**	|timestamp		|Creates key with provided long as an actual key and additional timestamp when key was created				|
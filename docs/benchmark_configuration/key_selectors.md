---
---

Key selectors
-------------

Key selectors are used by [key generators](./key_generators.html) as source of input to base final keys upon. Key selectors are mostly specified to define (limit) set of key values used but can also define the distribution of selection probability.
  
Where applicable default key selector can be overriden by placing following element into stages core element:

    <key-selector>
      <SELECTOR_ELEMENT_NAME />
    </key_selector>

#### Selectors

|**Selector name**		| Selector element name|Description											|
|-------------------------------|-----------------------|-----------------------------------------------------------------------------------------------|
|**CollidingKeysSelector**	|colliding-keys		|Provides same set of keys to all threads of the test						|
|**ConcurrentKeysSelector**	|concurrent-keys	|Provides different set of keys to each thread							|
|**GaussianKeysSelector**	|gaussian-keys		|Provides same set of keys to all threads of the test with configurable gaussian distribution	|
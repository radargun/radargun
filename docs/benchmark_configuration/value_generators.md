---
---

Value generators
----------------

Value generators generate random values of specific format that are to be later inserted into target application. Certain stages can only be used with specific Value generators and vice versa, refer to comments below.
  
Where applicable default value generator can be overriden by placing following element into stages core element:

    <value-generator>
      <GENERATOR_ELEMENT_NAME />
    </value-generator>

#### Generators

|**Generator name**		| Generator element name|Description														|
|-------------------------------|-----------------------|-----------------------------------------------------------------------------------------------------------------------|
|**BooleanValueGenerator**	|bool			|Generates random boolean values											|
|**ByteArrayValueGenerator**	|byte-array		|Generates random byte arrays												|
|**CacheAwareTextGenerator**	|cache-aware-text	|Generates values containing specified cache name									|
|**ComposedObjectGenerator**	|composed		|Creates composed values (containing numeric and text values)								|
|**DateValueGenerator**		|date			|Generates random Date values												|
|**IntegerValueGenerator**	|integer		|Generates random Integers												|
|**JpaValueGenerator**		|jpa			|Instantiates JPA entities. The constructor for the entities must match to the generateValue() method			|
|**ManyIntegersObjectGenerator**|many-integers		|Generates objects with specified number of random integers (specific for query extension)				|
|**NumberObjectGenerator**	|number-object		|Generates specific objects containing random integer and double value within set limits (specific for query extension)	|
|**RandomStreamGenerator**	|randomStream		|Generates stream of random data, (specific to stream operation benchmarking)						|
|**WrappedArrayValueGenerator**	|wrapped-array		|Generates random byte array wrapped in object that correctly implements equals() and hashCode()			|
|**SentenceGenerator**		|sentence		|Generates text-objects with string from randomly picked words								|
|**SingleWordGenerator**	|single-word		|Generates text-objects with single randomly picked word								|
|**WordInHaystackGenerator**	|word-in-haystack	|Generates text-objects with string with single randomly picked word surrounded by another characters			|
---
---

Namespaces
----------

With more traits and different services, as well as with the number of stages growing the need to organize stages has come. Traditionally the whole benchmark was covered with one XML schema, stages using prefixes or postfixes to describe their target traits without any strict concept.

Plugins and reporters already use their XML schemas for configuration of services, and using XML namespaces is the XML-natural way to organize stages, too.

When XML schemas are generated, these all should end up in distribution directory in the *schema/* subdirectory. For convenience, there's a directory symlink *schema* in root project directory that points to this directory so that IDE can recognize the generated XSD as source.

### Essential namespaces

The root benchmark namespace is `urn:radargun:benchmark:VERSION`, with `benchmark` as the root element. This contains the `master`, `cluster`, `configuration`, `init`, `scenario`, `destroy`, `cleanup` and `reports` elements. Actually, the `init`, `destroy` and `cleanup` elements extend a type defined in the `urn:radargun:stages:core:VERSION` namespace, that contains all the basic stages not specific to particular service type, e.g. those related to service lifecycle, monitoring etc. Note that cache-like traits living in core in the past have been moved to separate module, *extensions/cache*.

When the benchmark does not contain the scenario directly but instead imports another file using `<scenario url="..."<`, the target file should use the `urn:radargun:scenario:VERSION` namespace which has `scenario` as a root element.

Although you can use any namespace prefix, the convention is to use prefix `rg` for both `urn:radargun:benchmark:VERSION` and `urn:radargun:scenario:VERSION` since these two won't end up in the same file and describe similar elements (most notably the `repeat` element), and `urn:radargun:stages:core:VERSION` for the core stages.

### Extension namespaces

Stages from each module end up by default in the module namespace that is `urn:radargun:stages:MODULE:VERSION`, e.g. `urn:radargun:stages:cache:3.0` for the *radargun-cache* module. When it is desired to use multiple namespaces within one module, stage class can be annotated with `@Namespace` annotation. The value of the annotation is the full namespace name, e.g. `@Namespace("urn:radargun:stages:foo:3.0")` (so it is possible to have namespace without the `urn:radargun:stages` prefix, although it is not recommended). In the future, we plan to add an option to set namespace for whole package in `package-info.java`, but this is not implemented so far.

The stage name should *not* contain the module name anymore - for example the stage `CacheClearStage` has been renamed to `ClearStage` and in the benchmark file it is used as `<cache:clear .../<` instead of `<cache-clear .../<`. Note the use of prefix `cache`; you can use `xmlns:xxx="urn:radargun:stages:cache:3.0` as well, but using module name as the prefix is the most readable.

The custom namespaces have been used for *legacy* stress test, that have been moved to separate namespace `urn:radargun:stages:legacy:VERSION` in order to prevent mixing the 'new' stress tests and the 'legacy' ones. Example benchmarks that still use *legacy* stages use just `l` as namespace prefix.

Map-Reduce, Distributed Execution and Streams ended up in the *cache* module as caches are currently the only services that implement these traits. In future, the stages can be moved to their own extension modules.

### Plugins and reporters

Nothing has changed here, but there's no more the need to update symlinks to XSDs manually - the modules do not contain the symlink anymore, *schema* does the job.



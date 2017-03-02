---
path_to_root: ""
---

RadarGun
====================

Radargun 1.x was a tool for comparing the performance of different caching/data grid products (e.g. Infinispan, EHCache, Coherence). Since version 2.0 it has become a general distributed system testing tool, although the primary focus on implementation is still on clustered caches. Version 3.0 (development in progress) will bring support for testing other areas as well (e.g. JPA).

The goal is that end users configure the test framework based on their own cache usage needs so comparisons are extremely relevant.

Radargun has been successfully used for benchmarking performance under heavy load on clusters of 100+ nodes.

### Status

Version 3.0.0 (in development) will contain design improvements to extend our territory (e.g. JPA performance testing). New version will provide better isolation between service runs and enhance the way performance is measured. 3.x branch will be based on Java 8.

[RadarGun 2.1.0](https://github.com/radargun/radargun/releases/download/RadarGun-2.1.0.Final/RadarGun-2.1.0.Final.zip) supports both embedded and client-server scenarios, provides generic way of specifying the functionality (called traits) and pluggable mechanisms for both reporting and external tests. Current version, fully supported.

[RadarGun 1.1.0](https://github.com/radargun/radargun/releases/download/RadarGun-1.1.0.Final/RadarGun-1.1.0.Final.zip) The 1.x branch is maintained for bugfixes but no new features will be added.

### Reporting bugs

Please use [GitHub's IssueTracker](https://github.com/radargun/radargun/issues).

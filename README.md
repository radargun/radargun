# RadarGun
RadarGun is a data grid and distributed cache benchmarking framework built to test [Infinispan](http://www.infinispan.org) and other distributed data grid platforms.

## Documentation
All information on this framework, including usage, is maintained online, on [the project's wiki system](https://github.com/infinispan/radargun/wiki).

## Quick start:
To build, run:

    $ mvn clean install

and the distribution will be built in ``target/distribution`` which can then be used to perform benchmarks.

# CacheBenchFwk
This project is essentially a migration of the earlier - and far more poorly named - CacheBenchFwk on SourceForge.  

## TODOs
As a part of the migration, as you may imagine, there are a fair few outstanding tasks still pending.  Any help here is always much appreciated.

* Update POMs to use Maven's Release Plugin (the same way [infinispan-archetypes](http://github.com/infinispan/infinispan-archetypes) does)
  * Release plugin to generate version data for Version.java
* Test, test, test.  Mostly all that's gone on is simple find-and-replace renaming, but proper testing would be necessary.
* Revisit docs on the wiki.  This too has moved, from SourceForge's TRAC to GitHub's Markdown-formatted wiki (thanks to a handy script).  Some of the formatting conversion isn't 100% though and casting a human eye over it would be best.
  * Would also need to rename _cachebench_ -> _radargun_ on the wiki.
* Cut a release once this is done!
* Move any outstanding TRAC issues to GitHub's issues?  There should only be a very small handful.


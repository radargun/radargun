# Couchbase Client Plugin

This plugin allows Radargun to run performance tests against Couchbase server using the Couchbase Java SDK (version 2.7.4).

## Creating a Cluster

### Docker

Couchbase server can be setup and configured using Docker, with official images available on [DockerHub](https://hub.docker.com/_/couchbase). Instructions on how to start the container are available on there too.

### Manual install

Couchbase server can also be installed using a direct download available from [Couchbase.com/downloads](https://www.couchbase.com/downloads). Download and install instructions are available on the website.

## Setting up the Cluster

Once you have a running cluster, you need to setup the cluster before it can service requests. This is done by opening the web UI at [localhost:8091](http://localhost:8091) and following create new cluster forms (only the KV service is required to run Radargun tests).

## Creating the 'default' bucket

By default, the plugin expects to use a bucket called _default_ to store and retrieve values from. You can create a bucket using the *bucket* tab and selecting _new bucket_.

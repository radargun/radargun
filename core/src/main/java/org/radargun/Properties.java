package org.radargun;

/**
 * These properties are set on each worker as system property for the duration of benchmark execution.
 * You can use them when declaring an expression as property definition, e.g.
 * <my-stage foo="${worker.index}" />
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface Properties {
   String PROPERTY_CLUSTER_SIZE = "cluster.size";
   String PROPERTY_CLUSTER_MAX_SIZE = "cluster.maxSize";
   String PROPERTY_WORKER_INDEX = "worker.index";
   String PROPERTY_GROUP_PREFIX = "group.";
   String PROPERTY_GROUP_NAME = "group.name";
   String PROPERTY_GROUP_SIZE = "group.size";
   String PROPERTY_SIZE_SUFFIX = ".size";
   String PROPERTY_PLUGIN_NAME = "plugin.name";
   String PROPERTY_CONFIG_NAME = "config.name";
   String PROPERTY_PROCESS_ID = "process.id";
}

package org.radargun.config;

/**
 * Constants shared between *ConfigParser and ConfigSchemaGenerator
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
interface ConfigSchema {
   String ATTR_BIND_ADDRESS = "bindAddress";
   String ATTR_FROM = "from";
   String ATTR_GROUP = "group";
   String ATTR_INC = "inc";
   String ATTR_NAME = "name";
   String ATTR_PORT = "port";
   String ATTR_PLUGIN = "plugin";
   String ATTR_SIZE = "size";
   String ATTR_TO = "to";
   String ATTR_TIMES = "times";
   String ATTR_TYPE = "type";

   String ELEMENT_BENCHMARK = "benchmark";
   String ELEMENT_CLEANUP = "cleanup";
   String ELEMENT_CLUSTER = "cluster";
   String ELEMENT_CLUSTERS = "clusters";
   String ELEMENT_CONFIG = "config";
   String ELEMENT_CONFIGURATIONS = "configurations";
   String ELEMENT_DESTROY = "destroy";
   String ELEMENT_GROUP = "group";
   String ELEMENT_INIT = "init";
   String ELEMENT_MASTER = "master";
   String ELEMENT_REPEAT = "repeat";
   String ELEMENT_REPORT = "report";
   String ELEMENT_REPORTER = "reporter";
   String ELEMENT_REPORTS = "reports";
   String ELEMENT_SCALE = "scale";
   String ELEMENT_SCENARIO = "scenario";
   String ELEMENT_SETUP = "setup";
   String ELEMENT_VM_ARGS = "vm-args";
}

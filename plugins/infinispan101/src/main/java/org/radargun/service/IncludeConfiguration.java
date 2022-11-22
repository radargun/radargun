package org.radargun.service;

public enum IncludeConfiguration {

   JGROUPS_DEFAULT("server-configuration/jgroups/default.xml"),
   CACHE_CONTAINER_DEFAULT("server-configuration/cache-container/default.xml"),
   INTERFACES_DEFAULT("server-configuration/interfaces/default.xml"),
   SOCKET_BINDINGS_DEFAULT("server-configuration/socket-bindings/default.xml"),
   ENDPOINTS_DEFAULT("server-configuration/endpoints/default.xml"),
   SECURITY_DEFAULT("server-configuration/security/default.xml");

   private String value;

   IncludeConfiguration(String value) {
      this.value = value;
   }

   public String getValue() {
      return this.value;
   }

}

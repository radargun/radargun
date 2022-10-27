package org.radargun.service;

public class InfinispanCacheManagerInfo {

   private String membersString;
   private String nodeAddress;

   public InfinispanCacheManagerInfo() {
      this.membersString = "";
      this.nodeAddress = "";
   }

   public InfinispanCacheManagerInfo(String membersString, String nodeAddress) {
      this.membersString = membersString;
      this.nodeAddress = nodeAddress;
   }

   public String getMembersString() {
      return membersString;
   }

   public String getNodeAddress() {
      return nodeAddress;
   }
}

package org.radargun.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Clustered;

/**
 * @author Gustavo Lira &lt;glira@redhat.com&gt;
 */
public class Infinispan100ServerClustered implements Clustered {
   private final Log log = LogFactory.getLog(getClass());
   private InfinispanRestAPI restAPI;
   protected final Infinispan100ServerService service;
   protected final List<Membership> membershipHistory = new ArrayList<>();
   protected String lastMembers;
   protected CacheManagerInfo cacheManagerInfo;

   public Infinispan100ServerClustered(Infinispan100ServerService service, Integer defaultPort) {
      this.service = service;
      this.restAPI = new InfinispanRestAPI(defaultPort);
      service.schedule(new Runnable() {
         @Override
         public void run() {
            checkAndUpdateMembership();
         }
      }, service.viewCheckPeriod);
   }

   @Override
   public boolean isCoordinator() {
      if (!service.lifecycle.isRunning() || !cacheManagerInfo.isRunning()) {
         return false;
      }

      return cacheManagerInfo.isCoordinator();
   }

   protected Collection<Member> checkAndUpdateMembership() {
      if (!service.lifecycle.isRunning()) {
         synchronized (this) {
            if (!membershipHistory.isEmpty()
                  && membershipHistory.get(membershipHistory.size() - 1).members.size() > 0) {
               lastMembers = null;
               membershipHistory.add(Membership.empty());
            }
         }
         return Collections.emptyList();
      }

      cacheManagerInfo = restAPI.getCacheManager();
      try {

         if (cacheManagerInfo.getName() != null) {
            String membersString = cacheManagerInfo.getClusterMembers().toString();
            String nodeAddress = cacheManagerInfo.getNodeAddress();
            synchronized (this) {
               if (lastMembers != null && lastMembers.equals(membersString)) {
                  return membershipHistory.get(membershipHistory.size() - 1).members;
               }
               String[] nodes;
               if (!membersString.startsWith("[") || !membersString.endsWith("]")) {
                  nodes = new String[] { membersString };
               } else {
                  nodes = membersString.substring(1, membersString.length() - 1).split(",", 0);
               }
               lastMembers = membersString;

               ArrayList<Member> members = new ArrayList<>();
               for (int i = 0; i < nodes.length; ++i) {
                  members.add(new Member(nodes[i].trim(), nodes[i].equals(nodeAddress), i == 0));
               }
               membershipHistory.add(Membership.create(members));
               return members;
            }
         } else {
            return Collections.emptyList();
         }
      } catch (Exception e) {
         log.error("Failed to retrieve data from JMX", e);
         return null;
      }
   }

   @Override
   public Collection<Member> getMembers() {
      return checkAndUpdateMembership();
   }

   @Override
   public synchronized List<Membership> getMembershipHistory() {
      return new ArrayList<>(membershipHistory);
   }

}

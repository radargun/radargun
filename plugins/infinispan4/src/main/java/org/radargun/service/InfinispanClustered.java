package org.radargun.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachemanagerlistener.annotation.ViewChanged;
import org.infinispan.notifications.cachemanagerlistener.event.ViewChangedEvent;
import org.infinispan.remoting.transport.Address;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Clustered;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Listener
public class InfinispanClustered implements Clustered {

   protected static final Log log = LogFactory.getLog(InfinispanClustered.class);

   protected final InfinispanEmbeddedService service;
   protected final List<Membership> membershipHistory = new ArrayList<Membership>();

   public InfinispanClustered(InfinispanEmbeddedService service) {
      this.service = service;
   }

   @Override
   public boolean isCoordinator() {
      return service.cacheManager.isCoordinator();
   }

   @ViewChanged
   public synchronized void viewChanged(ViewChangedEvent e) {
      membershipHistory.add(Membership.create(convert(e.getNewMembers())));
   }

   public synchronized void stopped() {
      membershipHistory.add(Membership.empty());
   }

   @Override
   public synchronized Collection<Member> getMembers() {
      if (membershipHistory.isEmpty()) return null;
      return membershipHistory.get(membershipHistory.size() - 1).members;
   }

   private Collection<Member> convert(List<Address> addresses) {
      Collection<Member> members = new ArrayList<>(addresses.size());
      boolean coord = true;
      for (Address address : addresses) {
         members.add(new Member(address.toString(), service.cacheManager.getAddress().equals(address), coord));
         coord = false;
      }
      return members;
   }

   @Override
   public synchronized List<Membership> getMembershipHistory() {
      return new ArrayList<>(membershipHistory);
   }
}

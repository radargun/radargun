package org.radargun.service;

import java.util.List;

import org.jgroups.Address;
import org.jgroups.View;
import org.radargun.traits.Clustered;

public interface JGroupsReceiver {

   void updateLocalAddr(Address localAddr);

   void updateMyRank(int myRank);

   List<Clustered.Membership> getMembershipHistory();

   int getMyRank();

   Address getLocalAddr();

   List<Address> getMembers();

   void viewAccepted(View newView);
}

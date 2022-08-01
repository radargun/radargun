package org.radargun.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.View;
import org.jgroups.util.Util;
import org.radargun.traits.Clustered;

public class JGroups36Receiver implements JGroupsReceiver {

   protected volatile Address localAddr;
   protected volatile int myRank; // rank of current member in view
   protected volatile List<Address> members = Collections.emptyList();
   protected List<Clustered.Membership> membershipHistory = new ArrayList<>();
   private final JChannel ch;

   public JGroups36Receiver(JChannel ch) {
      this.ch = ch;
   }

   @Override
   public void updateLocalAddr(Address localAddr) {
      this.localAddr = localAddr;
   }

   @Override
   public void updateMyRank(int myRank) {
      this.myRank = myRank;
   }

   @Override
   public Address getLocalAddr() {
      return this.localAddr;
   }

   @Override
   public List<Clustered.Membership> getMembershipHistory() {
      return membershipHistory;
   }

   @Override
   public int getMyRank() {
      return myRank;
   }

   @Override
   public List<Address> getMembers() {
      return members;
   }

   @Override
   public void viewAccepted(View newView) {
      this.members = newView.getMembers();
      updateMyRank(Util.getRank(newView, getLocalAddr()) - 1);
      ArrayList<Clustered.Member> mbrs = new ArrayList<>(newView.getMembers().size());
      boolean coord = true;
      for (Address address : newView.getMembers()) {
         mbrs.add(new Clustered.Member(address.toString(), ch.getAddress().equals(address), coord));
         coord = false;
      }
      synchronized (this) {
         getMembershipHistory().add(Clustered.Membership.create(mbrs));
      }
   }
}

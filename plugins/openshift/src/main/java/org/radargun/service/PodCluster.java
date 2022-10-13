package org.radargun.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Pod;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Clustered;
import org.radargun.utils.Utils;

public class PodCluster implements Clustered {
   protected final Log log = LogFactory.getLog(getClass());
   protected final OpenShiftService service;
   protected final List<Membership> membershipHistory = new ArrayList<>();
   private ScheduledExecutorService executor;
   private List<Member> members;

   public PodCluster(OpenShiftService service) {
      this.service = service;
      executor = new ScheduledThreadPoolExecutor(1);
   }

   public void startMembershipUpdates() {
      executor.scheduleAtFixedRate(
         new Runnable() {
            @Override
            public void run() {
               updateMembership();
            }
         },0, 5000, TimeUnit.MILLISECONDS);
   }

   @Override
   public boolean isCoordinator() {
      return true;
   }

   protected void updateMembership() {
      if (!service.isRunning() || service.podsSelector.isEmpty()) {
         return;
      }
      try {
         synchronized (this) {
            List<Pod> readyPods = service.getReadyPods(service.podsSelector);
            members = toMembers(readyPods);
            membershipHistory.add(Membership.create(members));
            log.tracef("Pods ready: %d", members.size());
         }
      } catch (Exception e) {
         log.error("Failed to retrieve ready pods with label " + service.podsSelector, e);
      }
   }

   private List<Member> toMembers(List<Pod> readyPods) {
      return readyPods.stream().map(pod ->
         new Member(pod.getMetadata().getName(), false, false))
         .collect(Collectors.toList());
   }

   @Override
   public synchronized Collection<Member> getMembers() {
      if (!service.isRunning()) {
         return Collections.EMPTY_LIST;
      }
      return members;
   }

   @Override
   public synchronized List<Membership> getMembershipHistory() {
      return new ArrayList<>(membershipHistory);
   }

   public void stopMembershipUpdates() {
      Utils.shutdownAndWait(executor);
   }
}

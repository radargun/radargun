package org.radargun.stages.helpers;

import java.util.Collection;

import org.radargun.state.WorkerState;
import org.radargun.traits.Clustered;

public class RoleHelper {

   public enum Role {
      COORDINATOR
   }

   public static final String SUPPORTED_ROLES = "[COORDINATOR]";

   private RoleHelper() {}

   public static boolean hasAnyRole(WorkerState workerState, Collection<Role> roles) {
      for (Role role : roles) {
         switch (role) {
            case COORDINATOR:
               Clustered clustered = workerState.getTrait(Clustered.class);
               return clustered != null && clustered.isCoordinator();
         }
         throw new IllegalArgumentException("Role " + role + " is not supported");
      }
      return false;
   }
}

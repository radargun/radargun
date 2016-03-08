package org.radargun.stages.helpers;

import java.util.Collection;

import org.radargun.state.SlaveState;
import org.radargun.traits.Clustered;

public class RoleHelper {

   public enum Role {
      COORDINATOR
   }

   public static final String SUPPORTED_ROLES = "[COORDINATOR]";

   private RoleHelper() {}

   public static boolean hasAnyRole(SlaveState slaveState, Collection<Role> roles) {
      for (Role role : roles) {
         switch (role) {
            case COORDINATOR:
               Clustered clustered = slaveState.getTrait(Clustered.class);
               return clustered != null && clustered.isCoordinator();
         }
         throw new IllegalArgumentException("Role " + role + " is not supported");
      }
      return false;
   }
}

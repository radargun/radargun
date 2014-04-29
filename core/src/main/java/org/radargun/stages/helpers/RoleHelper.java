package org.radargun.stages.helpers;

import org.radargun.state.SlaveState;
import org.radargun.traits.Clustered;

public class RoleHelper {

   public enum Role {
      COORDINATOR
   }

   public final static String SUPPORTED_ROLES = "[COORDINATOR]";

   private RoleHelper() {}

   public static boolean hasRole(SlaveState slaveState, Role role) {
      if (role == null) return false;
      if (role == Role.COORDINATOR) {
         Clustered clustered = slaveState.getTrait(Clustered.class);
         return clustered != null && clustered.isCoordinator();
      }
      throw new IllegalArgumentException("Role " + role + " is not supported");
   }
}

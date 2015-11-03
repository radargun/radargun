package org.radargun.stages.test;

/**
 * Utility class that defines {@link #hashCode()}, {@link #equals(Object)} and {@link #toString()}
 * as instances of the same Conversation are usually equal to each other.
 */
public abstract class AbstractConversation implements Conversation {
   @Override
   public int hashCode() {
      return getClass().hashCode();
   }

   @Override
   public boolean equals(Object obj) {
      return obj != null && getClass().equals(obj.getClass());
   }

   @Override
   public String toString() {
      return getClass().getSimpleName();
   }
}

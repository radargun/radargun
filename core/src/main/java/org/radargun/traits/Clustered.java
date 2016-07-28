package org.radargun.traits;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Trait(doc = "Information about clustered nodes.")
public interface Clustered {
   /**
    * @return True if this slave has unique role in the cluster
    */
   boolean isCoordinator();

   /**
    * @return Collection of actual members. Equal to the last entry in {@link #getMembershipHistory()}.
    *         If null, the membership information is unknown.
    */
   Collection<Member> getMembers();

   /**
    * @return Append-only sequence of membership changes.
    */
   List<Membership> getMembershipHistory();

   class Member {
      /**
       * Plugin-specific name
       */
      public final String name;
      /**
       * True if the member is on this slave.
       */
      public final boolean local;
      /**
       * True if this member is the leader in the cluster.
       */
      public final boolean coordinator;

      // TODO: it would be very nice to have getSlaveIndex() here, but we cannot find that out for remote nodes

      public Member(String name, boolean local, boolean coordinator) {
         this.name = name;
         this.local = local;
         this.coordinator = coordinator;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;

         Member member = (Member) o;

         if (!name.equals(member.name)) return false;

         return true;
      }

      @Override
      public int hashCode() {
         return name.hashCode();
      }

      @Override
      public String toString() {
         final StringBuilder sb = new StringBuilder();
         sb.append(name).append("(local=").append(local);
         sb.append(", coord=").append(coordinator);
         sb.append(')');
         return sb.toString();
      }
   }

   class Membership {
      private static final ThreadLocal<DateFormat> FORMATTER = new ThreadLocal<DateFormat>() {
         @Override
         protected DateFormat initialValue() {
            return new SimpleDateFormat("HH:mm:ss,S");
         }
      };
      public final Date date;
      public final Collection<Member> members;

      public Membership(Date date, Collection<Member> members) {
         this.date = date;
         this.members = members;
      }

      public static Membership empty() {
         return new Membership(new Date(), Collections.EMPTY_LIST);
      }

      public static Membership create(Collection<Member> members) {
         return new Membership(new Date(), Collections.unmodifiableCollection(members));
      }

      @Override
      public String toString() {
         final StringBuilder sb = new StringBuilder();
         sb.append(FORMATTER.get().format(date)).append(' ').append(members);
         return sb.toString();
      }
   }
}

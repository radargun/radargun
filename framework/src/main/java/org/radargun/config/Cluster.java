package org.radargun.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Definition of one cluster
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Cluster implements Serializable {

   public final static String DEFAULT_GROUP = "default";

   List<Group> groups = new ArrayList<Group>();

   public void addGroup(String name, int size) {
      if (!groups.isEmpty() && DEFAULT_GROUP.equals(groups.get(0).name)) {
         throw new IllegalStateException("The cluster already contains default group (its size has been set).");
      }
      groups.add(new Group(name, size));
   }

   public void setSize(int size) {
      if (groups.isEmpty()) {
         groups.add(new Group(DEFAULT_GROUP, size));
      } else {
         Group group = groups.get(0);
         if (DEFAULT_GROUP.equals(group.name)) {
            throw new IllegalStateException("Size for default group already set to " + group.size);
         } else {
            throw new IllegalStateException("Cluster already contains some non-default groups: " + groups);
         }
      }
   }

   public int getSize() {
      int totalSize = 0;
      for (Group group : groups) {
         totalSize += group.size;
      }
      return totalSize;
   }

   public Group getGroup(int slaveIndex) {
      int index = slaveIndex;
      for (Group g : groups) {
         if (index < g.size) {
            return g;
         }
         index -= g.size;
      }
      throw new IllegalStateException("Slave index is " + slaveIndex + ", cluster is " + toString());
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder("Cluster[");
      for (int i = 0; i < groups.size(); ++i) {
         sb.append(groups.get(i).name).append('=').append(groups.get(i).size);
         if (i != groups.size() - 1) {
            sb.append(", ");
         }
      }
      return sb.append("]").toString();
   }

   public class Group implements Serializable {
      public final String name;
      public final int size;

      public Group(String name, int size) {
         this.name = name;
         this.size = size;
      }
   }
}

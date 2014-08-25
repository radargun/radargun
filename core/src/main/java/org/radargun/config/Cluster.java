package org.radargun.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Definition of one cluster
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Cluster implements Serializable, Comparable<Cluster> {
   private final static AtomicInteger indexGenerator = new AtomicInteger(0);
   public final static String DEFAULT_GROUP = "default";
   public final static Cluster LOCAL = new Cluster();

   static {
      LOCAL.addGroup("local", 1);
   }

   private List<Group> groups = new ArrayList<Group>();
   private final int index;

   public Cluster() {
      index = indexGenerator.getAndIncrement();
   }

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

   public int getIndexInGroup(int slaveIndex) {
      int index = slaveIndex;
      for (Group g : groups) {
         if (index < g.size) {
            return index;
         }
         index -= g.size;
      }
      throw new IllegalStateException("Slave index is " + slaveIndex + ", cluster is " + toString());
   }

   public int getClusterIndex() {
      return index;
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

   public List<Group> getGroups() {
      return Collections.unmodifiableList(groups);
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Cluster cluster = (Cluster) o;

      if (!groups.equals(cluster.groups)) return false;

      return true;
   }

   @Override
   public int hashCode() {
      return groups.hashCode();
   }

   @Override
   public int compareTo(Cluster c) {
      int compare = Integer.compare(getSize(), c.getSize());
      if (compare != 0) return compare;
      compare = Integer.compare(groups.size(), c.groups.size());
      if (compare != 0) return compare;
      for (int i = 0; i < groups.size(); ++i) {
         compare = Integer.compare(groups.get(i).size, c.groups.get(i).size);
         if (compare != 0) return compare;
      }
      return 0;
   }

   public Set<Integer> getSlaves(String group) {
      int index = 0;
      for (Group g : groups) {
         if (g.name.equals(group)) {
            Set<Integer> slaves = new HashSet<>(g.size);
            for (int i = 0; i < g.size; ++i) {
               slaves.add(index + i);
            }
            return slaves;
         }
         index += g.size;
      }
      throw new IllegalArgumentException("Group " + group + " was not defined");
   }

   public class Group implements Serializable {
      public final String name;
      public final int size;

      public Group(String name, int size) {
         this.name = name;
         this.size = size;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;

         Group group = (Group) o;

         if (size != group.size) return false;
         if (!name.equals(group.name)) return false;

         return true;
      }
   }
}

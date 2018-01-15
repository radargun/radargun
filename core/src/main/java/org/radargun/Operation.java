package org.radargun;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstraction of any operation executed through traits. Each operation
 * has ID (this may differ on different nodes) and name (that should be unique).
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public final class Operation implements Serializable {
   private static int nextId = 1;
   private static ConcurrentHashMap<Integer, Operation> byId = new ConcurrentHashMap<>();
   private static ConcurrentHashMap<String, Operation> byName = new ConcurrentHashMap<>();
   public static final Operation UNKNOWN = new Operation(0, "UNKNOWN");

   public final int id;
   public final String name;

   static {
      byId.put(0, UNKNOWN);
      byName.put(UNKNOWN.name, UNKNOWN);
   }

   private Operation(int id, String name) {
      this.id = id;
      this.name = name;
   }

   // This is useful when running report.sh
   private Object readResolve() {
      if (!byId.containsKey(id)) byId.put(id, this);
      if (!byName.containsKey(name)) byName.put(name, this);
      return this;
   }

   /**
    * Remove all registered operations. WARNING: Should only be used when no clients access Operations class any longer (e.g. testing purposes)
    */
   public static synchronized void clear() {
      nextId = 1;
      byId.clear();
      byName.clear();
   }

   /**
    * Create a new operation with given name.
    *
    * @param name
    * @return New operation, or operation previously registered with this name.
    */
   public static synchronized Operation register(String name) {
      Operation operation = byName.get(name);
      if (operation != null) return operation;

      operation = new Operation(nextId++, name);
      byId.put(operation.id, operation);
      Operation other = byName.put(name, operation);
      if (other != null) {
         throw new IllegalArgumentException();
      }
      return operation;
   }

   /**
    * Retrieve operation with given ID.
    *
    * @param id
    * @return
    * @throws IllegalArgumentException if the operation ID was not registered
    */
   public static Operation getById(int id) {
      Operation operation = byId.get(id);
      if (operation == null) {
         throw new IllegalArgumentException("No operation for ID " + id);
      }
      return operation;
   }

   /**
    * Retrieve operation with given name. If this operation was not registered, register it.
    *
    * @param name
    * @return
    */
   public static Operation getByName(String name) {
      Operation operation = byName.get(name);
      if (operation == null) {
         operation = register(name);
      }
      return operation;
   }

   /**
    * Register a new operation with name derived from this operation
    * by appending '.' and the variant to the name.
    *
    * @param variant
    * @return
    */
   public Operation derive(String variant) {
      String variantName = name + "." + variant;
      Operation operation = byName.get(variantName);
      if (operation == null) {
         operation = register(variantName);
      }
      return operation;
   }

   @Override
   public String toString() {
      return name;
   }

   @Override
   public boolean equals(Object o) {
      if (o instanceof Operation) {
         Operation op = (Operation) o;
         return this.id == op.id;
      }
      return false;
   }

   @Override
   public int hashCode() {
      return id;
   }
}

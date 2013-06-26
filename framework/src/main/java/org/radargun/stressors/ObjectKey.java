package org.radargun.stressors;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * @author Mircea.Markus@jboss.com
 */
public class ObjectKey implements Externalizable {

   private long keyIndex;

   public ObjectKey(long keyIndex) {
      this.keyIndex = keyIndex;
   }

   @Override
   public void writeExternal(ObjectOutput objectOutput) throws IOException {
      objectOutput.writeLong(keyIndex);
   }

   @Override
   public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
      this.keyIndex = objectInput.readLong();
   }

   @Override
   public String toString() {
      return String.format("ObjectKey{%016X}", keyIndex);
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ObjectKey objectKey = (ObjectKey) o;

      if (keyIndex != objectKey.keyIndex) return false;
      return true;
   }

   @Override
   public int hashCode() {
      return (int) keyIndex;
   }

   /**
    * This is an index that uniquely identifies this key in the cluster.
    */
   public long getKeyIndexInCluster(int threadCountPerNode, int keysPerThread) {
      return keyIndex;
   }
}

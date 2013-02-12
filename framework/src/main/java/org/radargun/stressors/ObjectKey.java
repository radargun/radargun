package org.radargun.stressors;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * @author Mircea.Markus@jboss.com
 */
public class ObjectKey implements Externalizable {

   private int nodeIndex = -1;

   private int threadIndex;

   private long keyIndex;

   public ObjectKey(int nodeIndex, int threadIndex, long keyIndex) {
      this.nodeIndex = nodeIndex;
      this.threadIndex = threadIndex;
      this.keyIndex = keyIndex;
   }

   public ObjectKey(int threadIndex, long keyIndex) {
      this.threadIndex = threadIndex;
      this.keyIndex = keyIndex;
   }

   @Override
   public void writeExternal(ObjectOutput objectOutput) throws IOException {
      objectOutput.writeInt(nodeIndex);
      objectOutput.writeInt(threadIndex);
      objectOutput.writeLong(keyIndex);
   }

   @Override
   public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
      this.nodeIndex = objectInput.readInt();
      this.threadIndex = objectInput.readInt();
      this.keyIndex = objectInput.readLong();
   }

   @Override
   public String toString() {
      return "ObjectKey{" +
            "nodeIndex=" + nodeIndex +
            ", threadIndex=" + threadIndex +
            ", keyIndex=" + keyIndex +
            '}';
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ObjectKey objectKey = (ObjectKey) o;

      if (keyIndex != objectKey.keyIndex) return false;
      if (nodeIndex != objectKey.nodeIndex) return false;
      if (threadIndex != objectKey.threadIndex) return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = nodeIndex;
      result = 31 * result + threadIndex;
      result = 31 * result + (int) keyIndex;
      return result;
   }

   public int getNodeIndex() {
      return nodeIndex;
   }

   public int getThreadIndex() {
      return threadIndex;
   }

   public long getKeyIndex() {
      return keyIndex;
   }

   /**
    * This is an index that uniquely identifies this key in the cluster.
    */
   public long getKeyIndexInCluster(int threadCountPerNode, int keysPerThread) {
      return nodeIndex * threadCountPerNode * keysPerThread
            + threadIndex * keysPerThread
            + keyIndex;
   }
}

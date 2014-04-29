package org.radargun.jpa;

import org.radargun.stages.cache.generators.JpaKeyGenerator;

import javax.persistence.Embeddable;

/**
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
*/
@Embeddable
public class EmbeddableId extends JpaKeyGenerator.JpaKey {
   public String firstPart;
   public String secondPart;

   public EmbeddableId() {
   }

   public EmbeddableId(long key) {
      this.firstPart = String.format("key%016x", key);
      this.secondPart = String.valueOf(key);
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      EmbeddableId that = (EmbeddableId) o;

      if (firstPart != null ? !firstPart.equals(that.firstPart) : that.firstPart != null) return false;
      if (secondPart != null ? !secondPart.equals(that.secondPart) : that.secondPart != null) return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = firstPart != null ? firstPart.hashCode() : 0;
      result = 31 * result + (secondPart != null ? secondPart.hashCode() : 0);
      return result;
   }

   @Override
   public String toString() {
      return firstPart + '|' + secondPart;
   }
}

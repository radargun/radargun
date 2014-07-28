package org.radargun.jpa;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import java.util.Random;

import org.radargun.stages.cache.generators.JpaValueGenerator;

/**
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
*/
@Entity
public class EmbeddedIdEntity extends JpaValueGenerator.JpaValue {
   @EmbeddedId
   public EmbeddableId embeddableId;
   @Column(length = 65535)
   public String description;

   public EmbeddedIdEntity() {
   }

   public EmbeddedIdEntity(Object id, int size, Random random) {
      embeddableId = (EmbeddableId) id;
      description = JpaValueGenerator.getRandomString(size, random);
   }
}

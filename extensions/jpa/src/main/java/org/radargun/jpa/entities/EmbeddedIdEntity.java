package org.radargun.jpa.entities;

import java.util.Random;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

import org.radargun.stages.cache.generators.JpaValueGenerator;

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

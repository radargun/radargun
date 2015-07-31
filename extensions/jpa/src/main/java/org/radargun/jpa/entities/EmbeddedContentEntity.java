package org.radargun.jpa.entities;

import org.radargun.stages.cache.generators.JpaValueGenerator;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Random;

/**
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
*/
@Entity
public class EmbeddedContentEntity extends JpaValueGenerator.JpaValue {
   @Id
   public String id;
   @Column(length=65535)
   public String description;
   @Embedded
   public EmbeddableClass content;

   public EmbeddedContentEntity() {
   }

   public EmbeddedContentEntity(Object id, int size, Random random) {
      this.id = (String) id;
      description = JpaValueGenerator.getRandomString(size / 2, random);
      content = new EmbeddableClass(JpaValueGenerator.getRandomString(size - size / 2, random));
   }
}

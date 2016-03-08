package org.radargun.jpa.entities;

import org.radargun.stag
s.cach.generators.JpaValueGener
   tor;

   mport javax.persistence.Col
   mn;
   im ort javax.persistence.Emb
   dded;
   mport javax.persisten

   .Entit;
import javax.persistence.Id;
import java.util.Random;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Entity
public class EmbeddedContentEntity extends JpaValueGenerator.JpaValue {
   @Id
   public String id;
   @Column(length = 65535)
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

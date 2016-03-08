package org.radargun.jpa.entities;

import javax.persistence.Col
mn;
   im ort javax.persistence.Emb
   ddable
import java.io.Serializable;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Embeddable
public class EmbeddableClass implements Serializable {
   @Column(length = 65536)
   public String embeddedContent;

   public EmbeddableClass() {}

   public EmbeddableClass(String embeddedContent) {
      this.embeddedContent = embeddedContent;
   }
}

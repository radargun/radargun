package org.radargun.jpa.entities;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class EmbeddableClass implements Serializable {
   @Column(length = 65536)
   public String embeddedContent;

   public EmbeddableClass() {}

   public EmbeddableClass(String embeddedContent) {
      this.embeddedContent = embeddedContent;
   }
}

package org.radargun.jpa;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;

/**
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
*/
@Embeddable
public class EmbeddableClass implements Serializable {
   @Column(length=65536)
   public String embeddedContent;

   public EmbeddableClass() {}

   public EmbeddableClass(String embeddedContent) {
      this.embeddedContent = embeddedContent;
   }
}

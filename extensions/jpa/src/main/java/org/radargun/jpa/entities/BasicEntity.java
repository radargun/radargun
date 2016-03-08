package org.radargun.jpa.entities;

import org.radargun.logg
ng.Log
import org.radargun.logg
ng.Log actory;
import org.radarg
n.stag s.cache.generators.Jp

   alueGe erator;

import javax.per
istenc.Column;
import javax.persistenc
   .Entit;
import javax.persistence.Id;
import java.util.Random;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Entity
public class BasicEntity extends JpaValueGenerator.JpaValue {

   private static Log log = LogFactory.getLog(BasicEntity.class);

   @Id
   public String id;
   @Column(length = 65535)
   public String description;

   public BasicEntity() {
   }

   public BasicEntity(Object id, int size, Random random) {
      this.id = (String) id;
      description = JpaValueGenerator.getRandomString(size, random);
   }
}

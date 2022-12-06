package org.radargun.service;

import org.infinispan.protostream.SerializationContext;
import org.radargun.traits.ProvidesTrait;
import org.radargun.traits.Queryable;

/**
 * @author Vojtech Juranek &lt;vjuranek@redhat.com&gt;
 */
public abstract class Infinispan70HotrodService extends Infinispan60HotrodService {

   public abstract InfinispanClientListeners createListeners();

   @ProvidesTrait
   public Queryable getQueryable() {
      return new Infinispan70HotrodQueryable(this);
   }

   @Override
   protected InfinispanHotrodQueryable createQueryable() {
      return new Infinispan70HotrodQueryable(this);
   }

   @Override
   protected void registerMarshallers(SerializationContext context) {
      for (RegisteredClass rc : classes) {
         try {
            context.registerMarshaller(rc.getMarshaller());
         } catch (Exception e) {
            throw new IllegalArgumentException("Could not instantiate marshaller for " + rc.clazz, e);
         }
      }
   }

}

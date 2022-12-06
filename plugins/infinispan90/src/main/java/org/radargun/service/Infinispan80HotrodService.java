package org.radargun.service;

import org.infinispan.protostream.SerializationContext;
import org.radargun.traits.ContinuousQuery;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Martin Gencur &lt;mgencur@redhat.com&gt;
 */
public abstract class Infinispan80HotrodService extends Infinispan71HotrodService {

   @ProvidesTrait
   public Infinispan80ClientListeners createListeners() {
      return new Infinispan80ClientListeners(this);
   }

   @ProvidesTrait
   public abstract ContinuousQuery createContinuousQuery();

   @Override
   protected Infinispan80HotrodQueryable createQueryable() {
      return new Infinispan80HotrodQueryable(this);
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

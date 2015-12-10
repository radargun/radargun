package org.radargun.service;

import org.infinispan.protostream.SerializationContext;
import org.radargun.Service;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Vojtech Juranek &lt;vjuranek@redhat.com&gt;
 */
@Service(doc = JDG66HotrodService.SERVICE_DESCRIPTION)
public class JDG66HotrodService extends Infinispan71HotrodService {

    @ProvidesTrait
    public JDGHotrodContinuousQuery createContinuousQuery() {
        return new JDGHotrodContinuousQuery(this);
    }

    @Override
    protected JDG66HotrodQueryable createQueryable() {
        return new JDG66HotrodQueryable(this);
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

package org.radargun.service;

import org.infinispan.protostream.SerializationContext;
import org.radargun.Service;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Vojtech Juranek &lt;vjuranek@redhat.com&gt;
 */
@Service(doc = JDG66HotrodService.SERVICE_DESCRIPTION)
public class JDG66HotrodService extends Infinispan60HotrodService {

    @ProvidesTrait
    public InfinispanHotrodContinuousQuery createContinuousQuery() {
        return new InfinispanHotrodContinuousQuery(this);
    }

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

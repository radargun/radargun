package org.radargun.service;

import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.traits.ContinuousQuery;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Matej Cimbora
 */
@Service(doc = Infinispan60HotrodService.SERVICE_DESCRIPTION)
public class Infinispan90HotrodService extends Infinispan81HotrodService {

   @Property(doc = "Fully specified Marshaller class, if no class is defined, the default Marshaller - JBossMarshaller is used" +
           "valid options are org.infinispan.marshaller.protostuff.ProtostuffMarshaller and org.infinispan.marshaller.kryo.KryoMarshaller")
   protected String marshaller;

   @ProvidesTrait
   public ContinuousQuery createContinuousQuery() {
      return new Infinispan90HotrodContinuousQuery(this);
   }

   @Override
   protected ConfigurationBuilder getDefaultHotRodConfig() {
      ConfigurationBuilder defaultHotRodConfig = super.getDefaultHotRodConfig();

      if (marshaller != null) {
         defaultHotRodConfig.marshaller(marshaller);
         log.info("Marshaller is set to " + marshaller);
      }
      return defaultHotRodConfig;
   }
}

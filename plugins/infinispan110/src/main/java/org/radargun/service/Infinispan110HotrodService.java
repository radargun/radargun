package org.radargun.service;

import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.marshall.JavaSerializationMarshaller;
import org.radargun.Service;
import org.radargun.marshaller.LibraryInitializerImpl;
import org.radargun.traits.ProvidesTrait;
import org.radargun.traits.Queryable;

@Service(doc = Infinispan110HotrodService.SERVICE_DESCRIPTION)
public class Infinispan110HotrodService extends Infinispan100HotrodService {

   @Override
   protected ConfigurationBuilder getDefaultHotRodConfig() {
      ConfigurationBuilder builder = super.getDefaultHotRodConfig();
      builder.marshaller(new JavaSerializationMarshaller()).addJavaSerialWhiteList(".*");
      builder.addContextInitializer(new LibraryInitializerImpl());
      return builder;
   }

   @ProvidesTrait
   @Override
   public Queryable getQueryable() {
      return new Infinispan110HotrodQueryable(this);
   }
}

package org.radargun.service;

import java.io.IOException;
import java.io.InputStream;

import org.infinispan.commons.util.FileLookupFactory;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.radargun.Service;

/**
 * Infinispan 10.0.x Embedded Service
 *
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan100EmbeddedService extends Infinispan94EmbeddedService {

   @Override
   protected Infinispan100Lifecycle createLifecycle() {
      return new Infinispan100Lifecycle(this);
   }

   @Override
   protected ConfigurationBuilderHolder createConfiguration(String configFile) {
      ClassLoader classLoader = getClass().getClassLoader();
      try (InputStream input = FileLookupFactory.newInstance().lookupFileStrict(configFile, classLoader)) {
         ConfigurationBuilderHolder holder = new ParserRegistry().parse(input, null);
         return holder;
      } catch (IOException e) {
         log.error("Failed to get configuration input stream", e);
      }
      return null;
   }
}
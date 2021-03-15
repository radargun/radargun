package org.radargun.service;

import java.io.IOException;
import java.net.URL;

import org.infinispan.commons.util.FileLookupFactory;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.radargun.Service;

@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan121FailureEmbeddedService extends Infinispan110FailureEmbeddedService {

   @Override
   protected ConfigurationBuilderHolder createConfiguration(String configFile) {
      ClassLoader classLoader = getClass().getClassLoader();
      URL file = FileLookupFactory.newInstance().lookupFileLocation(configFile, classLoader);
      try {
         ConfigurationBuilderHolder holder = new ParserRegistry().parse(file);
         return holder;
      } catch (IOException e) {
         log.error("Failed to get configuration input stream", e);
         return null;
      }
   }
}

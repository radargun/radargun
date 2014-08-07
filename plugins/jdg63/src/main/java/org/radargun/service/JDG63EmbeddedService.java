package org.radargun.service;

import java.io.FileNotFoundException;
import java.io.InputStream;

import org.infinispan.commons.util.FileLookup;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.radargun.Service;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Service(doc = JDG63EmbeddedService.SERVICE_DESCRIPTION)
public class JDG63EmbeddedService extends Infinispan60EmbeddedService {
   protected static final String SERVICE_DESCRIPTION = "Service hosting JDG in embedded (library) mode.";

   @Override
   @ProvidesTrait
   public Infinispan70MapReduce createMapReduce() {
      return new Infinispan70MapReduce(this);
   }

   @Override
   protected ConfigurationBuilderHolder createConfiguration(String configFile) throws FileNotFoundException {
      ClassLoader classLoader = getClass().getClassLoader();
      InputStream input = new FileLookup().lookupFileStrict(configFile, classLoader);
      return new ParserRegistry(classLoader).parse(input);
   }

   @Override
   @ProvidesTrait
   public InfinispanEmbeddedQueryable createQueryable() {
      return new Infinispan70EmbeddedQueryable(this);
   }

   @Override
   @ProvidesTrait
   public InfinispanCacheInfo createCacheInformation() {
      return new Infinispan70CacheInfo(this);
   }
}

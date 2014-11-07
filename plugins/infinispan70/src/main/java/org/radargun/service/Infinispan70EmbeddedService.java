package org.radargun.service;

import java.io.FileNotFoundException;
import java.io.InputStream;

import org.infinispan.commons.util.FileLookup;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan70EmbeddedService extends Infinispan60EmbeddedService {

   @Override
   protected ConfigurationBuilderHolder createConfiguration(String configFile) throws FileNotFoundException {
      ClassLoader classLoader = getClass().getClassLoader();
      InputStream input = new FileLookup().lookupFileStrict(configFile, classLoader);
      return new ParserRegistry(classLoader).parse(input);
   }

   @Override
   @ProvidesTrait
   public Infinispan70MapReduce createMapReduce() {
      return new Infinispan70MapReduce(this);
   }

   @Override
   @ProvidesTrait
   public InfinispanCacheInfo createCacheInformation() {
      return new Infinispan70CacheInfo(this);
   }

   @Override
   @ProvidesTrait
   public InfinispanEmbeddedQueryable createQueryable() {
      return new Infinispan70EmbeddedQueryable(this);
   }

   @ProvidesTrait
   public InfinispanIterable createIterable() {
      return new InfinispanIterable(this);
   }

   @ProvidesTrait
   public InfinispanCacheListeners createListeners() {
      return new InfinispanCacheListeners(this);
   }

}

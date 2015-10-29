package org.radargun.service;

import org.radargun.Service;
import org.radargun.traits.ProvidesTrait;

/**
 *
 * @author vjuranek
 *
 */
@Service(doc = JDG64EmbeddedService.SERVICE_DESCRIPTION)
public class JDG66EmbeddedService extends JDG64EmbeddedService  {
   protected static final String SERVICE_DESCRIPTION = "Service hosting JDG in embedded (library) mode.";

   @ProvidesTrait
   public InfinispanEmbeddedQueryable createQueryable() {
      return new JDG66EmbeddedQueryable(this);
   }

}

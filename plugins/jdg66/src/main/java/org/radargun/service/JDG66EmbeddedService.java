package org.radargun.service;

import org.radargun.Service;
import org.radargun.traits.ProvidesTrait;

/**
 * @author vjuranek
 */
@Service(doc = JDG66EmbeddedService.SERVICE_DESCRIPTION)
public class JDG66EmbeddedService extends JDG64EmbeddedService {
   protected static final String SERVICE_DESCRIPTION = "Service hosting JDG in embedded (library) mode.";

   @ProvidesTrait
   public JDG66EmbeddedContinuousQuery createContinuousQuery() {
      return new JDG66EmbeddedContinuousQuery(this);
   }

   @ProvidesTrait
   public InfinispanEmbeddedQueryable createQueryable() {
      return new JDG66EmbeddedQueryable(this);
   }


}

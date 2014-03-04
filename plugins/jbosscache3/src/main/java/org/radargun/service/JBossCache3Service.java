package org.radargun.service;

import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Mircea.Markus@jboss.com
 */
@Service(doc = "JBossCache 3.x")
public class JBossCache3Service extends JBossCache2Service
{
   @Property(doc = "Use flat cache")
   protected boolean flatCache;

   @ProvidesTrait
   @Override
   public JBossCache2Operations createOperations() {
      return new JBossCache3Operations(this, flatCache);
   }
}

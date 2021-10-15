package org.radargun.service;

import org.radargun.Service;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Service(doc = InfinispanServerService.SERVICE_DESCRIPTION)
public class Infinispan80ServerService extends Infinispan60ServerService {
   @Override
   protected String getDefaultStartBashScript() {
      return "standalone." + (getWindows() ? "bat" : "sh");
   }
}

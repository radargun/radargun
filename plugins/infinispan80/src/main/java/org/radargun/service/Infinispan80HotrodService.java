package org.radargun.service;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.radargun.Service;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Martin Gencur &lt;mgencur@redhat.com&gt;
 */
@Service(doc = Infinispan60HotrodService.SERVICE_DESCRIPTION)
public class Infinispan80HotrodService extends Infinispan71HotrodService {

    @ProvidesTrait
    public Infinispan80ClientListeners createListeners() {
        return new Infinispan80ClientListeners(this);
    }

}

package org.radargun.service;

import org.infinispan.factories.annotations.InfinispanModule;
import org.infinispan.lifecycle.ModuleLifecycle;

@InfinispanModule(name = "ispn100-plugin", requiredModules = "core")
public class Infinispan100HookedJGroupsTransportLifecycleManager implements ModuleLifecycle {

}

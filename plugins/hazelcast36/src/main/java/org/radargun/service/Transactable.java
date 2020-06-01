package org.radargun.service;

import com.hazelcast.core.TransactionalMap;

public interface Transactable<K, V> {
   String name();
   Transactable<K, V> wrap(TransactionalMap<K, V> map);
}

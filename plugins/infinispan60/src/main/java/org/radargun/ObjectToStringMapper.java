package org.radargun;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.infinispan.persistence.keymappers.TwoWayKey2StringMapper;

/**
 * Hackish generic implementation - keeps all objects in a map for reverse mapping.
 * Requires that the key was stored before it is read from cachestore - no preloading!
 */
public class ObjectToStringMapper implements TwoWayKey2StringMapper {

   private ConcurrentMap<String, Object> map = new ConcurrentHashMap<String, Object>();

   @Override
   public boolean isSupportedType(Class<?> keyType) {
      return true;
   }

   @Override
   public String getStringMapping(Object key) {
      String string = key.toString();
      map.putIfAbsent(string, key);
      return string;
   }

   @Override
   public Object getKeyMapping(String stringKey) {
      return map.get(stringKey);
   }
}

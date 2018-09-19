package org.radargun.service.redisenterprise;

import org.radargun.stages.cache.generators.KeyGenerator;

public class RedisEnterpriseByteArrayKeyGenerator implements KeyGenerator {

   @Override
   public Object generateKey(long keyIndex) {
      return Long.toHexString(keyIndex).getBytes();
   }

}

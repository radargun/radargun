package org.radargun.utils;

/**
 * AddressListConverter for redis services with default port 6379
 * 
 * @author Jakub Markos &lt;jmarkos@redhat.com&gt;
 */
public class RedisAddressListConverter extends AddressListConverter {

   public RedisAddressListConverter() {
      super(6379);
   }

}

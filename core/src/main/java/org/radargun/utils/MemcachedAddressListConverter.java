package org.radargun.utils;

/**
 * AddressListConverter for memcached services with default port 11211
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public class MemcachedAddressListConverter extends AddressListConverter {

   public MemcachedAddressListConverter() {
      super(11211);
   }

}

package org.radargun.utils;

/**
 * AddressListConverter for REST services with default port 8080
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public class RESTAddressListConverter extends AddressListConverter {

   public RESTAddressListConverter() {
      super(8080);
   }

}

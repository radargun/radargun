package org.radargun.tpcc.dac;

import org.radargun.CacheWrapper;
import org.radargun.tpcc.TpccTools;
import org.radargun.tpcc.domain.Customer;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peluso@gsd.inesc-id.pt , peluso@dis.uniroma1.it
 */
public final class CustomerDAC {

   private CustomerDAC() {
   }

   public static List<Customer> loadByCLast(CacheWrapper cacheWrapper, long c_w_id, long c_d_id, String c_last) throws Throwable {

      List<Customer> result = new ArrayList<Customer>();

      Customer current = null;
      boolean found = false;

      for (int i = 1; i <= TpccTools.NB_MAX_CUSTOMER; i++) {

         current = new Customer();

         current.setC_id(i);
         current.setC_d_id(c_d_id);
         current.setC_w_id(c_w_id);

         found = current.load(cacheWrapper);

         if (found && current.getC_last() != null && current.getC_last().equals(c_last)) {

            result.add(current);

         }

      }

      return result;


   }

}

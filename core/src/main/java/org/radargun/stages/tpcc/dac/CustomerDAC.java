package org.radargun.stages.tpcc.dac;

import java.util.ArrayList;
import java.util.List;

import org.radargun.stages.tpcc.TpccTools;
import org.radargun.stages.tpcc.domain.Customer;
import org.radargun.traits.BasicOperations;

/**
 * @author Sebastiano Peluso &lt;peluso@gsd.inesc-id.pt, peluso@dis.uniroma1.it&gt;
 */
public final class CustomerDAC {

   private CustomerDAC() {
   }

   public static List<Customer> loadByCLast(BasicOperations.Cache basicCache, long c_w_id, long c_d_id, String c_last) throws Throwable {
      List<Customer> result = new ArrayList<Customer>();
      Customer current = null;
      boolean found = false;

      for (int i = 1; i <= TpccTools.NB_MAX_CUSTOMER; i++) {
         current = new Customer();
         current.setC_id(i);
         current.setC_d_id(c_d_id);
         current.setC_w_id(c_w_id);
         found = current.load(basicCache);
         if (found && current.getC_last() != null && current.getC_last().equals(c_last)) {
            result.add(current);
         }
      }
      return result;
   }
}

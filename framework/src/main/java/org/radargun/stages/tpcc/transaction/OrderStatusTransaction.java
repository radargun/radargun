package org.radargun.stages.tpcc.transaction;

import org.radargun.CacheWrapper;
import org.radargun.stages.tpcc.ElementNotFoundException;
import org.radargun.stages.tpcc.TpccTerminal;
import org.radargun.stages.tpcc.TpccTools;
import org.radargun.stages.tpcc.dac.CustomerDAC;
import org.radargun.stages.tpcc.dac.OrderDAC;
import org.radargun.stages.tpcc.dac.OrderLineDAC;
import org.radargun.stages.tpcc.domain.Customer;
import org.radargun.stages.tpcc.domain.Order;
import org.radargun.stages.tpcc.domain.OrderLine;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author peluso@gsd.inesc-id.pt , peluso@dis.uniroma1.it
 */
public class OrderStatusTransaction implements TpccTransaction {

   private long terminalWarehouseID;

   private long districtID;

   private String customerLastName;

   private long customerID;

   private boolean customerByName;

   public OrderStatusTransaction() {

      this.terminalWarehouseID = TpccTools.randomNumber(1, TpccTools.NB_WAREHOUSES);

      // clause 2.6.1.2
      this.districtID = TpccTools.randomNumber(1, TpccTools.NB_MAX_DISTRICT);

      long y = TpccTools.randomNumber(1, 100);
      this.customerLastName = null;
      this.customerID = -1;
      if (y <= 60) {
         // clause 2.6.1.2 (dot 1)
         this.customerByName = true;
         this.customerLastName = lastName((int) TpccTools.nonUniformRandom(TpccTools.C_C_LAST, TpccTools.A_C_LAST, 0, TpccTools.MAX_C_LAST));
      } else {
         // clause 2.6.1.2 (dot 2)
         customerByName = false;
         customerID = TpccTools.nonUniformRandom(TpccTools.C_C_ID, TpccTools.A_C_ID, 1, TpccTools.NB_MAX_CUSTOMER);
      }


   }

   @Override
   public void executeTransaction(CacheWrapper cacheWrapper) throws Throwable {

      orderStatusTransaction(cacheWrapper, terminalWarehouseID, districtID, customerID, customerLastName, customerByName);
   }

   @Override
   public boolean isReadOnly() {
      return true;
   }

   private String lastName(int num) {
      return TpccTerminal.nameTokens[num / 100] + TpccTerminal.nameTokens[(num / 10) % 10] + TpccTerminal.nameTokens[num % 10];
   }

   private void orderStatusTransaction(CacheWrapper cacheWrapper, long w_id, long d_id, long c_id, String c_last, boolean c_by_name) throws Throwable {
      long namecnt;

      boolean found = false;
      Customer c = null;
      if (c_by_name) {
         List<Customer> cList = CustomerDAC.loadByCLast(cacheWrapper, w_id, d_id, c_last);
         if (cList == null || cList.isEmpty())
            throw new ElementNotFoundException("C_LAST=" + c_last + " C_D_ID=" + d_id + " C_W_ID=" + w_id + " not found!");
         Collections.sort(cList);


         namecnt = cList.size();


         if (namecnt % 2 == 1) namecnt++;
         Iterator<Customer> itr = cList.iterator();

         for (int i = 1; i <= namecnt / 2; i++) {

            c = itr.next();

         }

      } else {
         // clause 2.6.2.2 (dot 3, Case 1)
         c = new Customer();
         c.setC_id(c_id);
         c.setC_d_id(d_id);
         c.setC_w_id(w_id);
         found = c.load(cacheWrapper);
         if (!found)
            throw new ElementNotFoundException("C_ID=" + c_id + " C_D_ID=" + d_id + " C_W_ID=" + w_id + " not found!");

      }

      // clause 2.6.2.2 (dot 4)
      Order o = OrderDAC.loadByGreatestId(cacheWrapper, w_id, d_id, c_id);

      // clause 2.6.2.2 (dot 5)
      List<OrderLine> o_lines = OrderLineDAC.loadByOrder(cacheWrapper, o);


   }


}

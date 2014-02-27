package org.radargun.stages.tpcc.transaction;

import org.radargun.CacheWrapper;
import org.radargun.stages.tpcc.ElementNotFoundException;
import org.radargun.stages.tpcc.TpccTools;
import org.radargun.stages.tpcc.domain.Customer;
import org.radargun.stages.tpcc.domain.District;
import org.radargun.stages.tpcc.domain.Item;
import org.radargun.stages.tpcc.domain.NewOrder;
import org.radargun.stages.tpcc.domain.Order;
import org.radargun.stages.tpcc.domain.OrderLine;
import org.radargun.stages.tpcc.domain.Stock;
import org.radargun.stages.tpcc.domain.Warehouse;

import java.util.Date;

/**
 * @author peluso@gsd.inesc-id.pt , peluso@dis.uniroma1.it
 */
public class NewOrderTransaction implements TpccTransaction {

   private long terminalWarehouseID;

   private long districtID;

   private long customerID;

   private int numItems;

   private int allLocal;

   private long[] itemIDs;

   private long[] supplierWarehouseIDs;

   private long[] orderQuantities;

   public NewOrderTransaction() {

      this.terminalWarehouseID = TpccTools.randomNumber(1, TpccTools.NB_WAREHOUSES);


      this.districtID = TpccTools.randomNumber(1, TpccTools.NB_MAX_DISTRICT);
      this.customerID = TpccTools.nonUniformRandom(TpccTools.C_C_ID, TpccTools.A_C_ID, 1, TpccTools.NB_MAX_CUSTOMER);

      this.numItems = (int) TpccTools.randomNumber(5, 15); // o_ol_cnt
      this.itemIDs = new long[numItems];
      this.supplierWarehouseIDs = new long[numItems];
      this.orderQuantities = new long[numItems];
      this.allLocal = 1; // see clause 2.4.2.2 (dot 6)
      for (int i = 0; i < numItems; i++) // clause 2.4.1.5
      {
         itemIDs[i] = TpccTools.nonUniformRandom(TpccTools.C_OL_I_ID, TpccTools.A_OL_I_ID, 1, TpccTools.NB_MAX_ITEM);
         if (TpccTools.randomNumber(1, 100) > 1) {
            supplierWarehouseIDs[i] = terminalWarehouseID;
         } else //see clause 2.4.1.5 (dot 2)
         {
            do {
               supplierWarehouseIDs[i] = TpccTools.randomNumber(1, TpccTools.NB_WAREHOUSES);
            }
            while (supplierWarehouseIDs[i] == terminalWarehouseID && TpccTools.NB_WAREHOUSES > 1);
            allLocal = 0;// see clause 2.4.2.2 (dot 6)
         }
         orderQuantities[i] = TpccTools.randomNumber(1, TpccTools.NB_MAX_DISTRICT); //see clause 2.4.1.5 (dot 6)
      }
      // clause 2.4.1.5 (dot 1)
      if (TpccTools.randomNumber(1, 100) == 1)
         this.itemIDs[this.numItems - 1] = -12345;

   }

   @Override
   public void executeTransaction(CacheWrapper cacheWrapper) throws Throwable {

      newOrderTransaction(cacheWrapper, terminalWarehouseID, districtID, customerID, numItems, allLocal, itemIDs, supplierWarehouseIDs, orderQuantities);


   }

   @Override
   public boolean isReadOnly() {
      return false;
   }

   private void newOrderTransaction(CacheWrapper cacheWrapper, long w_id, long d_id, long c_id, int o_ol_cnt, int o_all_local, long[] itemIDs, long[] supplierWarehouseIDs, long[] orderQuantities) throws Throwable {


      long o_id = -1, s_quantity;
      String i_data, s_data;

      String ol_dist_info = null;
      double[] itemPrices = new double[o_ol_cnt];
      double[] orderLineAmounts = new double[o_ol_cnt];
      String[] itemNames = new String[o_ol_cnt];
      long[] stockQuantities = new long[o_ol_cnt];
      char[] brandGeneric = new char[o_ol_cnt];
      long ol_supply_w_id, ol_i_id, ol_quantity;
      int s_remote_cnt_increment;
      double ol_amount, total_amount = 0;


      Customer c = new Customer();
      Warehouse w = new Warehouse();

      c.setC_id(c_id);
      c.setC_d_id(d_id);
      c.setC_w_id(w_id);

      boolean found = c.load(cacheWrapper);

      if (!found)
         throw new ElementNotFoundException("W_ID=" + w_id + " C_D_ID=" + d_id + " C_ID=" + c_id + " not found!");

      w.setW_id(w_id);

      found = w.load(cacheWrapper);
      if (!found) throw new ElementNotFoundException("W_ID=" + w_id + " not found!");


      District d = new District();
      // see clause 2.4.2.2 (dot 4)


      d.setD_id(d_id);
      d.setD_w_id(w_id);
      found = d.load(cacheWrapper);
      if (!found) throw new ElementNotFoundException("D_ID=" + d_id + " D_W_ID=" + w_id + " not found!");


      o_id = d.getD_next_o_id();


      NewOrder no = new NewOrder(o_id, d_id, w_id);

      no.store(cacheWrapper);

      d.setD_next_o_id(d.getD_next_o_id() + 1);

      d.store(cacheWrapper);


      Order o = new Order(o_id, d_id, w_id, c_id, new Date(), -1, o_ol_cnt, o_all_local);

      o.store(cacheWrapper);


      // see clause 2.4.2.2 (dot 8)
      for (int ol_number = 1; ol_number <= o_ol_cnt; ol_number++) {
         ol_supply_w_id = supplierWarehouseIDs[ol_number - 1];
         ol_i_id = itemIDs[ol_number - 1];
         ol_quantity = orderQuantities[ol_number - 1];

         // clause 2.4.2.2 (dot 8.1)
         Item i = new Item();
         i.setI_id(ol_i_id);
         found = i.load(cacheWrapper);
         if (!found) throw new ElementNotFoundException("I_ID=" + ol_i_id + " not found!");


         itemPrices[ol_number - 1] = i.getI_price();
         itemNames[ol_number - 1] = i.getI_name();
         // clause 2.4.2.2 (dot 8.2)

         Stock s = new Stock();
         s.setS_i_id(ol_i_id);
         s.setS_w_id(ol_supply_w_id);
         found = s.load(cacheWrapper);
         if (!found) throw new ElementNotFoundException("I_ID=" + ol_i_id + " not found!");


         s_quantity = s.getS_quantity();
         stockQuantities[ol_number - 1] = s_quantity;
         // clause 2.4.2.2 (dot 8.2)
         if (s_quantity - ol_quantity >= 10) {
            s_quantity -= ol_quantity;
         } else {
            s_quantity += -ol_quantity + 91;
         }

         if (ol_supply_w_id == w_id) {
            s_remote_cnt_increment = 0;
         } else {
            s_remote_cnt_increment = 1;
         }
         // clause 2.4.2.2 (dot 8.2)
         s.setS_quantity(s_quantity);
         s.setS_ytd(s.getS_ytd() + ol_quantity);
         s.setS_remote_cnt(s.getS_remote_cnt() + s_remote_cnt_increment);
         s.setS_order_cnt(s.getS_order_cnt() + 1);
         s.store(cacheWrapper);


         // clause 2.4.2.2 (dot 8.3)
         ol_amount = ol_quantity * i.getI_price();
         orderLineAmounts[ol_number - 1] = ol_amount;
         total_amount += ol_amount;
         // clause 2.4.2.2 (dot 8.4)
         i_data = i.getI_data();
         s_data = s.getS_data();
         if (i_data.contains(TpccTools.ORIGINAL) && s_data.contains(TpccTools.ORIGINAL)) {
            brandGeneric[ol_number - 1] = 'B';
         } else {
            brandGeneric[ol_number - 1] = 'G';
         }

         switch ((int) d_id) {
            case 1:
               ol_dist_info = s.getS_dist_01();
               break;
            case 2:
               ol_dist_info = s.getS_dist_02();
               break;
            case 3:
               ol_dist_info = s.getS_dist_03();
               break;
            case 4:
               ol_dist_info = s.getS_dist_04();
               break;
            case 5:
               ol_dist_info = s.getS_dist_05();
               break;
            case 6:
               ol_dist_info = s.getS_dist_06();
               break;
            case 7:
               ol_dist_info = s.getS_dist_07();
               break;
            case 8:
               ol_dist_info = s.getS_dist_08();
               break;
            case 9:
               ol_dist_info = s.getS_dist_09();
               break;
            case 10:
               ol_dist_info = s.getS_dist_10();
               break;
         }
         // clause 2.4.2.2 (dot 8.5)

         OrderLine ol = new OrderLine(o_id, d_id, w_id, ol_number, ol_i_id, ol_supply_w_id, null, ol_quantity, ol_amount, ol_dist_info);
         ol.store(cacheWrapper);

      }

   }


}

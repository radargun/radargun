package org.radargun.tpcc.transaction;

import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.radargun.CacheWrapper;
import org.radargun.tpcc.ElementNotFoundException;
import org.radargun.tpcc.TpccTerminal;
import org.radargun.tpcc.TpccTools;
import org.radargun.tpcc.dac.CustomerDAC;
import org.radargun.tpcc.domain.Customer;
import org.radargun.tpcc.domain.District;
import org.radargun.tpcc.domain.History;
import org.radargun.tpcc.domain.Warehouse;

/**
 * 
 * @author peluso@gsd.inesc-id.pt , peluso@dis.uniroma1.it
 */
public class PaymentTransaction implements TpccTransaction {

   private long terminalWarehouseID;

   private long districtID;

   private long customerDistrictID;

   private long customerWarehouseID;

   private long customerID;

   private boolean customerByName;

   private String customerLastName;

   private double paymentAmount;

   private int slaveIndex;

   public PaymentTransaction(int slaveIndex){

      this.slaveIndex = slaveIndex;

      this.terminalWarehouseID=TpccTools.randomNumber(1,TpccTools.NB_WAREHOUSES);

      this.districtID = TpccTools.randomNumber(1, TpccTools.NB_MAX_DISTRICT);

      long x = TpccTools.randomNumber(1, 100);

      if(x <= 85)
      {
         this.customerDistrictID = this.districtID;
         this.customerWarehouseID = this.terminalWarehouseID;
      }
      else
      {
         this.customerDistrictID = TpccTools.randomNumber(1, TpccTools.NB_MAX_DISTRICT);
         do
         {
            this.customerWarehouseID = TpccTools.randomNumber(1, TpccTools.NB_WAREHOUSES);
         }
         while(this.customerWarehouseID == this.terminalWarehouseID && TpccTools.NB_WAREHOUSES > 1);
      }

      long y = TpccTools.randomNumber(1, 100);


      this.customerID = -1;
      this.customerLastName = null;
      if(y <= 60)
      {
         this.customerByName = true;
         customerLastName = lastName((int)TpccTools.nonUniformRandom(TpccTools.C_C_LAST, TpccTools.A_C_LAST, 0, TpccTools.MAX_C_LAST));
      }
      else
      {
         this.customerByName = false;
         this.customerID = TpccTools.nonUniformRandom(TpccTools.C_C_ID, TpccTools.A_C_ID, 1, TpccTools.NB_MAX_CUSTOMER);
      }

      this.paymentAmount = TpccTools.randomNumber(100, 500000)/100.0;


   }

   @Override
   public void executeTransaction(CacheWrapper cacheWrapper) throws Throwable {

      paymentTransaction(cacheWrapper, terminalWarehouseID, customerWarehouseID, paymentAmount, districtID, customerDistrictID, customerID, customerLastName, customerByName);

   }
   
   @Override
   public boolean isReadOnly(){
      return false;
   }

   private String lastName(int num) {
      return TpccTerminal.nameTokens[num/100] + TpccTerminal.nameTokens[(num/10)%10] + TpccTerminal.nameTokens[num%10];
   }


   private void paymentTransaction(CacheWrapper cacheWrapper, long w_id, long c_w_id, double h_amount, long d_id, long c_d_id, long c_id, String c_last, boolean c_by_name)  throws Throwable {
      String w_name;
      String d_name;
      long namecnt;

      String new_c_last;

      String c_data = null, c_new_data, h_data;


      Warehouse w=new Warehouse();
      w.setW_id(w_id);

      boolean found=w.load(cacheWrapper);
      if(!found) throw new ElementNotFoundException("W_ID=" + w_id + " not found!");
      w.setW_ytd(h_amount);
      w.store(cacheWrapper);


      District d=new District();
      d.setD_id(d_id);
      d.setD_w_id(w_id);
      found=d.load(cacheWrapper);
      if(!found) throw new ElementNotFoundException("D_ID=" + d_id + " D_W_ID=" + w_id + " not found!");

      d.setD_ytd(h_amount);
      d.store(cacheWrapper);



      Customer c=null;

      if(c_by_name)
      {
         new_c_last=c_last;
         List cList=null;
         cList=CustomerDAC.loadByCLast(cacheWrapper, c_w_id, c_d_id, new_c_last);

         if(cList==null || cList.isEmpty()) throw new ElementNotFoundException("C_LAST=" + c_last + " C_D_ID=" + c_d_id + " C_W_ID=" + c_w_id + " not found!");

         Collections.sort(cList);


         namecnt=cList.size();



         if(namecnt%2 == 1) namecnt++;
         Iterator<Customer> itr=cList.iterator();

         for(int i = 1; i <= namecnt / 2; i++){

            c=itr.next();

         }

      }
      else
      {

         c=new Customer();
         c.setC_id(c_id);
         c.setC_d_id(c_d_id);
         c.setC_w_id(c_w_id);
         found=c.load(cacheWrapper);
         if(!found) throw new ElementNotFoundException("C_ID=" + c_id + " C_D_ID=" + c_d_id + " C_W_ID=" + c_w_id + " not found!");


      }


      c.setC_balance(c.getC_balance()+h_amount);
      if(c.getC_credit().equals("BC"))
      {

         c_data=c.getC_data();

         c_new_data = c.getC_id() + " " + c_d_id + " " + c_w_id + " " + d_id + " " + w_id  + " " + h_amount + " |";
         if(c_data.length() > c_new_data.length())
         {
            c_new_data += c_data.substring(0, c_data.length()-c_new_data.length());
         }
         else
         {
            c_new_data += c_data;
         }

         if(c_new_data.length() > 500) c_new_data = c_new_data.substring(0, 500);

         c.setC_data(c_new_data);

         c.store(cacheWrapper);


      }
      else
      {
         c.store(cacheWrapper);

      }

      w_name=w.getW_name();
      d_name=d.getD_name();

      if(w_name.length() > 10) w_name = w_name.substring(0, 10);
      if(d_name.length() > 10) d_name = d_name.substring(0, 10);
      h_data = w_name + "    " + d_name;

      History h=new History(c.getC_id(), c_d_id, c_w_id, d_id, w_id, new Date(), h_amount, h_data);
      h.store(cacheWrapper, this.slaveIndex);



   }

}

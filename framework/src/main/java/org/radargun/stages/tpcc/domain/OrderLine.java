package org.radargun.stages.tpcc.domain;

import org.radargun.CacheWrapper;

import java.io.Serializable;
import java.util.Date;

/**
 * @author peluso@gsd.inesc-id.pt , peluso@dis.uniroma1.it
 */
public class OrderLine implements Serializable {

   private long ol_o_id;

   private long ol_d_id;

   private long ol_w_id;

   private long ol_number;

   private long ol_i_id;

   private long ol_supply_w_id;

   private long ol_delivery_d;

   private long ol_quantity;

   private double ol_amount;

   private String ol_dist_info;


   public OrderLine() {
   }

   public OrderLine(long ol_o_id, long ol_d_id, long ol_w_id, long ol_number, long ol_i_id, long ol_supply_w_id, Date ol_delivery_d, long ol_quantity, double ol_amount, String ol_dist_info) {
      this.ol_o_id = ol_o_id;
      this.ol_d_id = ol_d_id;
      this.ol_w_id = ol_w_id;
      this.ol_number = ol_number;
      this.ol_i_id = ol_i_id;
      this.ol_supply_w_id = ol_supply_w_id;
      this.ol_delivery_d = (ol_delivery_d == null) ? -1 : ol_delivery_d.getTime();
      this.ol_quantity = ol_quantity;
      this.ol_amount = ol_amount;
      this.ol_dist_info = ol_dist_info;
   }

   public long getOl_o_id() {
      return ol_o_id;
   }

   public long getOl_d_id() {
      return ol_d_id;
   }

   public long getOl_w_id() {
      return ol_w_id;
   }

   public long getOl_number() {
      return ol_number;
   }

   public long getOl_i_id() {
      return ol_i_id;
   }

   public long getOl_supply_w_id() {
      return ol_supply_w_id;
   }

   public Date getOl_delivery_d() {
      return (ol_delivery_d == -1) ? null : new Date(ol_delivery_d);
   }

   public long getOl_quantity() {
      return ol_quantity;
   }

   public double getOl_amount() {
      return ol_amount;
   }

   public String getOl_dist_info() {
      return ol_dist_info;
   }

   public void setOl_o_id(long ol_o_id) {
      this.ol_o_id = ol_o_id;
   }

   public void setOl_d_id(long ol_d_id) {
      this.ol_d_id = ol_d_id;
   }

   public void setOl_w_id(long ol_w_id) {
      this.ol_w_id = ol_w_id;
   }

   public void setOl_number(long ol_number) {
      this.ol_number = ol_number;
   }

   public void setOl_i_id(long ol_i_id) {
      this.ol_i_id = ol_i_id;
   }

   public void setOl_supply_w_id(long ol_supply_w_id) {
      this.ol_supply_w_id = ol_supply_w_id;
   }

   public void setOl_delivery_d(Date ol_delivery_d) {
      this.ol_delivery_d = (ol_delivery_d == null) ? -1 : ol_delivery_d.getTime();
   }

   public void setOl_quantity(long ol_quantity) {
      this.ol_quantity = ol_quantity;
   }

   public void setOl_amount(double ol_amount) {
      this.ol_amount = ol_amount;
   }

   public void setOl_dist_info(String ol_dist_info) {
      this.ol_dist_info = ol_dist_info;
   }

   private String getKey() {
      return "ORDERLINE_" + this.ol_w_id + "_" + this.ol_d_id + "_" + this.ol_o_id + "_" + this.ol_number;
   }

   public void store(CacheWrapper wrapper) throws Throwable {

      wrapper.put(null, this.getKey(), this);
   }

   public boolean load(CacheWrapper wrapper) throws Throwable {

      OrderLine loaded = (OrderLine) wrapper.get(null, this.getKey());

      if (loaded == null) return false;

      this.ol_i_id = loaded.ol_i_id;
      this.ol_supply_w_id = loaded.ol_supply_w_id;
      this.ol_delivery_d = loaded.ol_delivery_d;
      this.ol_quantity = loaded.ol_quantity;
      this.ol_amount = loaded.ol_amount;
      this.ol_dist_info = loaded.ol_dist_info;


      return true;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      OrderLine orderLine = (OrderLine) o;

      if (Double.compare(orderLine.ol_amount, ol_amount) != 0) return false;
      if (ol_d_id != orderLine.ol_d_id) return false;
      if (ol_delivery_d != orderLine.ol_delivery_d) return false;
      if (ol_i_id != orderLine.ol_i_id) return false;
      if (ol_number != orderLine.ol_number) return false;
      if (ol_o_id != orderLine.ol_o_id) return false;
      if (ol_quantity != orderLine.ol_quantity) return false;
      if (ol_supply_w_id != orderLine.ol_supply_w_id) return false;
      if (ol_w_id != orderLine.ol_w_id) return false;
      if (ol_dist_info != null ? !ol_dist_info.equals(orderLine.ol_dist_info) : orderLine.ol_dist_info != null)
         return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result;
      long temp;
      result = (int) (ol_o_id ^ (ol_o_id >>> 32));
      result = 31 * result + (int) (ol_d_id ^ (ol_d_id >>> 32));
      result = 31 * result + (int) (ol_w_id ^ (ol_w_id >>> 32));
      result = 31 * result + (int) (ol_number ^ (ol_number >>> 32));
      result = 31 * result + (int) (ol_i_id ^ (ol_i_id >>> 32));
      result = 31 * result + (int) (ol_supply_w_id ^ (ol_supply_w_id >>> 32));
      result = 31 * result + (int) (ol_delivery_d ^ (ol_delivery_d >>> 32));
      result = 31 * result + (int) (ol_quantity ^ (ol_quantity >>> 32));
      temp = ol_amount != +0.0d ? Double.doubleToLongBits(ol_amount) : 0L;
      result = 31 * result + (int) (temp ^ (temp >>> 32));
      result = 31 * result + (ol_dist_info != null ? ol_dist_info.hashCode() : 0);
      return result;
   }


}

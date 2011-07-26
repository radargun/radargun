package org.ragargun.tpcc.domain;

import java.io.Serializable;

import org.radargun.CacheWrapper;

/**
 * 
 *
 * @author peluso@gsd.inesc-id.pt , peluso@dis.uniroma1.it
 */
public class Item implements Serializable {
   
   private long i_id;

   private long i_im_id;

   private String i_name;

   private double i_price;

   private String i_data;

   public Item() {

   }

   public Item(long i_id, long i_im_id, String i_name, double i_price, String i_data) {
      this.i_id = i_id;
      this.i_im_id = i_im_id;
      this.i_name = i_name;
      this.i_price = i_price;
      this.i_data = i_data;
   }

   public long getI_id() {
      return i_id;
   }

   public long getI_im_id() {
      return i_im_id;
   }

   public String getI_name() {
      return i_name;
   }

   public double getI_price() {
      return i_price;
   }

   public String getI_data() {
      return i_data;
   }

   public void setI_id(long i_id) {
      this.i_id = i_id;
   }

   public void setI_im_id(long i_im_id) {
      this.i_im_id = i_im_id;
   }

   public void setI_name(String i_name) {
      this.i_name = i_name;
   }

   public void setI_price(double i_price) {
      this.i_price = i_price;
   }

   public void setI_data(String i_data) {
      this.i_data = i_data;
   }

   private String getKey(){

      return "ITEM_"+this.i_id;
   }

   public void store(CacheWrapper wrapper) throws Throwable{


      wrapper.put(null, this.getKey(), this);


   }

   public boolean load(CacheWrapper wrapper)throws Throwable{

      Item loaded=(Item)wrapper.get(null,this.getKey());

      if(loaded==null) return false;

      this.i_data=loaded.i_data;
      this.i_im_id=loaded.i_im_id;
      this.i_name=loaded.i_name;
      this.i_price=loaded.i_price;


      return true;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Item item = (Item) o;

      if (i_id != item.i_id) return false;
      if (i_im_id != item.i_im_id) return false;
      if (Double.compare(item.i_price, i_price) != 0) return false;
      if (i_data != null ? !i_data.equals(item.i_data) : item.i_data != null) return false;
      if (i_name != null ? !i_name.equals(item.i_name) : item.i_name != null) return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result;
      long temp;
      result = (int) (i_id ^ (i_id >>> 32));
      result = 31 * result + (int) (i_im_id ^ (i_im_id >>> 32));
      result = 31 * result + (i_name != null ? i_name.hashCode() : 0);
      temp = i_price != +0.0d ? Double.doubleToLongBits(i_price) : 0L;
      result = 31 * result + (int) (temp ^ (temp >>> 32));
      result = 31 * result + (i_data != null ? i_data.hashCode() : 0);
      return result;
   }

}

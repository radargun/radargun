package org.ragargun.tpcc.domain;

import java.io.Serializable;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import org.radargun.CacheWrapper;

/**
 * 
 *
 * @author peluso@gsd.inesc-id.pt , peluso@dis.uniroma1.it
 */
public class History implements Serializable {
   
   private static final AtomicLong idGenerator= new AtomicLong(0L);

   private long h_c_id;
   
   private long h_c_d_id;
   
   private long h_c_w_id;
   
   private long h_d_id;
   
   private long h_w_id;
   
   private long h_date;
   
   private double h_amount;
   
   private String h_data;
   

   public History() {
      
   }

   public History(long h_c_id, long h_c_d_id, long h_c_w_id, long h_d_id, long h_w_id, Date h_date, double h_amount, String h_data) {
      this.h_c_id = h_c_id;
      this.h_c_d_id = h_c_d_id;
      this.h_c_w_id = h_c_w_id;
      this.h_d_id = h_d_id;
      this.h_w_id = h_w_id;
      this.h_date = (h_date==null)?-1:h_date.getTime();
      this.h_amount = h_amount;
      this.h_data = h_data;
   }

   public long getH_c_id() {
      return h_c_id;
   }

   public long getH_c_d_id() {
      return h_c_d_id;
   }

   public long getH_c_w_id() {
      return h_c_w_id;
   }

   public long getH_d_id() {
      return h_d_id;
   }

   public long getH_w_id() {
      return h_w_id;
   }

   public Date getH_date() {
      return (h_date==-1)?null:new Date(h_date);
   }

   public double getH_amount() {
      return h_amount;
   }

   public String getH_data() {
      return h_data;
   }

   public void setH_c_id(long h_c_id) {
      this.h_c_id = h_c_id;
   }

   public void setH_c_d_id(long h_c_d_id) {
      this.h_c_d_id = h_c_d_id;
   }

   public void setH_c_w_id(long h_c_w_id) {
      this.h_c_w_id = h_c_w_id;
   }

   public void setH_d_id(long h_d_id) {
      this.h_d_id = h_d_id;
   }

   public void setH_w_id(long h_w_id) {
      this.h_w_id = h_w_id;
   }

   public void setH_date(Date h_date) {
      this.h_date = (h_date==null)?-1:h_date.getTime();
   }

   public void setH_amount(double h_amount) {
      this.h_amount = h_amount;
   }

   public void setH_data(String h_data) {
      this.h_data = h_data;
   }

   private static String generateId(int slaveIndex){

      return String.valueOf(slaveIndex)+String.valueOf(History.idGenerator.incrementAndGet());
   }

   public void store(CacheWrapper wrapper, int slaveIndex)throws Throwable{
      String id=generateId(slaveIndex);
      wrapper.put(null, id, this);
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      History history = (History) o;

      if (Double.compare(history.h_amount, h_amount) != 0) return false;
      if (h_c_d_id != history.h_c_d_id) return false;
      if (h_c_id != history.h_c_id) return false;
      if (h_c_w_id != history.h_c_w_id) return false;
      if (h_d_id != history.h_d_id) return false;
      if (h_date != history.h_date) return false;
      if (h_w_id != history.h_w_id) return false;
      if (h_data != null ? !h_data.equals(history.h_data) : history.h_data != null) return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result;
      long temp;
      result = (int) (h_c_id ^ (h_c_id >>> 32));
      result = 31 * result + (int) (h_c_d_id ^ (h_c_d_id >>> 32));
      result = 31 * result + (int) (h_c_w_id ^ (h_c_w_id >>> 32));
      result = 31 * result + (int) (h_d_id ^ (h_d_id >>> 32));
      result = 31 * result + (int) (h_w_id ^ (h_w_id >>> 32));
      result = 31 * result + (int) (h_date ^ (h_date >>> 32));
      temp = h_amount != +0.0d ? Double.doubleToLongBits(h_amount) : 0L;
      result = 31 * result + (int) (temp ^ (temp >>> 32));
      result = 31 * result + (h_data != null ? h_data.hashCode() : 0);
      return result;
   }


}

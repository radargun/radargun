package org.radargun.stages.tpcc.domain;

import java.io.Serializable;

import org.radargun.traits.BasicOperations;

/**
 * @author Sebastiano Peluso &lt;peluso@gsd.inesc-id.pt, peluso@dis.uniroma1.it&gt;
 */
public class District implements Serializable {

   private long d_id;

   /* warehouse id */
   private long d_w_id;

   /* size 10 */
   private String d_name;

   /* max size 20 */
   private String d_street1;

   /* max size 20 */
   private String d_street2;

   /* max size 20 */
   private String d_city;

   /* size 2 */
   private String d_state;

   /* size 9 */
   private String d_zip;

   private double d_tax;

   private double d_ytd;

   private long d_next_o_id;


   public District() {
   }

   public District(long d_w_id, long d_id, String d_name, String d_street1, String d_street2, String d_city, String d_state, String d_zip, double d_tax, double d_ytd, long d_next_o_id) {
      this.d_w_id = d_w_id;
      this.d_id = d_id;
      this.d_name = d_name;
      this.d_street1 = d_street1;
      this.d_street2 = d_street2;
      this.d_city = d_city;
      this.d_state = d_state;
      this.d_zip = d_zip;
      this.d_tax = d_tax;
      this.d_ytd = d_ytd;
      this.d_next_o_id = d_next_o_id;
   }


   public long getD_w_id() {
      return d_w_id;
   }

   public long getD_id() {
      return d_id;
   }

   public String getD_name() {
      return d_name;
   }

   public String getD_street1() {
      return d_street1;
   }

   public String getD_street2() {
      return d_street2;
   }

   public String getD_city() {
      return d_city;
   }

   public String getD_state() {
      return d_state;
   }

   public String getD_zip() {
      return d_zip;
   }

   public double getD_tax() {
      return d_tax;
   }

   public double getD_ytd() {
      return d_ytd;
   }

   public long getD_next_o_id() {
      return d_next_o_id;
   }

   public void setD_w_id(long d_w_id) {
      this.d_w_id = d_w_id;
   }

   public void setD_id(long d_id) {
      this.d_id = d_id;
   }

   public void setD_name(String d_name) {
      this.d_name = d_name;
   }

   public void setD_street1(String d_street1) {
      this.d_street1 = d_street1;
   }

   public void setD_street2(String d_street2) {
      this.d_street2 = d_street2;
   }

   public void setD_city(String d_city) {
      this.d_city = d_city;
   }

   public void setD_state(String d_state) {
      this.d_state = d_state;
   }

   public void setD_zip(String d_zip) {
      this.d_zip = d_zip;
   }

   public void setD_tax(double d_tax) {
      this.d_tax = d_tax;
   }

   public void setD_ytd(double d_ytd) {
      this.d_ytd = d_ytd;
   }

   public void setD_next_o_id(long d_next_o_id) {
      this.d_next_o_id = d_next_o_id;
   }

   private String getKey() {
      return "DISTRICT_" + this.d_w_id + "_" + this.d_id;
   }

   public void store(BasicOperations.Cache basicCache) throws Throwable {
      basicCache.put(this.getKey(), this);
   }

   public boolean load(BasicOperations.Cache basicCache) throws Throwable {
      District loaded = (District) basicCache.get(this.getKey());
      if (loaded == null) return false;

      this.d_city = loaded.d_city;
      this.d_name = loaded.d_name;
      this.d_next_o_id = loaded.d_next_o_id;
      this.d_state = loaded.d_state;
      this.d_street1 = loaded.d_street1;
      this.d_street2 = loaded.d_street2;
      this.d_tax = loaded.d_tax;
      this.d_ytd = loaded.d_ytd;
      this.d_zip = loaded.d_zip;

      return true;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      District district = (District) o;

      if (d_id != district.d_id) return false;
      if (d_next_o_id != district.d_next_o_id) return false;
      if (Double.compare(district.d_tax, d_tax) != 0) return false;
      if (d_w_id != district.d_w_id) return false;
      if (Double.compare(district.d_ytd, d_ytd) != 0) return false;
      if (d_city != null ? !d_city.equals(district.d_city) : district.d_city != null) return false;
      if (d_name != null ? !d_name.equals(district.d_name) : district.d_name != null) return false;
      if (d_state != null ? !d_state.equals(district.d_state) : district.d_state != null) return false;
      if (d_street1 != null ? !d_street1.equals(district.d_street1) : district.d_street1 != null) return false;
      if (d_street2 != null ? !d_street2.equals(district.d_street2) : district.d_street2 != null) return false;
      if (d_zip != null ? !d_zip.equals(district.d_zip) : district.d_zip != null) return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result;
      long temp;
      result = (int) (d_w_id ^ (d_w_id >>> 32));
      result = 31 * result + (int) (d_id ^ (d_id >>> 32));
      result = 31 * result + (d_name != null ? d_name.hashCode() : 0);
      result = 31 * result + (d_street1 != null ? d_street1.hashCode() : 0);
      result = 31 * result + (d_street2 != null ? d_street2.hashCode() : 0);
      result = 31 * result + (d_city != null ? d_city.hashCode() : 0);
      result = 31 * result + (d_state != null ? d_state.hashCode() : 0);
      result = 31 * result + (d_zip != null ? d_zip.hashCode() : 0);
      temp = d_tax != +0.0d ? Double.doubleToLongBits(d_tax) : 0L;
      result = 31 * result + (int) (temp ^ (temp >>> 32));
      temp = d_ytd != +0.0d ? Double.doubleToLongBits(d_ytd) : 0L;
      result = 31 * result + (int) (temp ^ (temp >>> 32));
      result = 31 * result + (int) (d_next_o_id ^ (d_next_o_id >>> 32));
      return result;
   }


}

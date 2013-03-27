package org.radargun.stressors;

/**
* Enum for operations that can be executed on the cache wrapper
*
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
* @since 1/28/13
*/
public enum Operation {
   GET("READ"),
   GET_NULL("READ_NULL"),
   PUT("WRITE"),
   REMOVE,
   REMOVE_VALID,
   REMOVE_INVALID,
   PUT_IF_ABSENT_IS_ABSENT,
   PUT_IF_ABSENT_NOT_ABSENT,
   REPLACE_VALID, /* the old value supplied is the correct one */
   REPLACE_INVALID, /* the old value is an incorrect one */
   GET_ALL,
   GET_ALL_VIA_ASYNC,
   PUT_ALL,
   PUT_ALL_VIA_ASYNC,
   REMOVE_ALL,
   REMOVE_ALL_VIA_ASYNC,
   TRANSACTION; /* stats for whole transaction */

   private String altName;

   Operation() {}

   Operation(String altName) {
      this.altName = altName;
   }

   public String getAltName() {
      if (this.altName == null) return name();
      return altName;
   }
}

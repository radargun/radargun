package org.radargun.stressors;

/**
* Enum for operations that can be executed on the cache wrapper
*
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
* @since 1/28/13
*/
public enum Operation {
   GET("READ"),
   PUT("WRITE"),
   REMOVE,
   REMOVE_VALID,
   REMOVE_INVALID,
   PUT_IF_ABSENT_IS_ABSENT,
   PUT_IF_ABSENT_NOT_ABSENT,
   REPLACE_VALID, /* the old value supplied is the correct one */
   REPLACE_INVALID, /* the old value is an incorrect one */
   GET_ALL,
   PUT_ALL,
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

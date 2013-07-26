package org.radargun.stressors;

/**
* Enum for operations that can be executed on the cache wrapper
*
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
* @since 1/28/13
*/
public enum Operation {
   GET("READ"),
   GET_ASYNC(true),
   GET_NULL("READ_NULL"),
   PUT("WRITE"),
   PUT_ASYNC(true),
   REMOVE,
   REMOVE_ASYNC(true),
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
   QUERY,
   TRANSACTION; /* stats for whole transaction */

   private String altName;
   private boolean async;

   Operation() {}

   Operation(String altName) {
      this.altName = altName;
   }
   
   Operation(boolean async) {
       this.async = async;
    }
   
   public boolean isAsync(){
       return async;
   }

   public String getAltName() {
      if (this.altName == null) return name();
      return altName;
   }
}

package org.radargun.stages.tpcc;

/**
 * @author peluso@gsd.inesc-id.pt , peluso@dis.uniroma1.it
 */
public class ElementNotFoundException extends Exception {

   public ElementNotFoundException() {
      super();
   }

   public ElementNotFoundException(String message) {
      super(message);
   }

   public ElementNotFoundException(String message, Throwable cause) {
      super(message, cause);
   }

   public ElementNotFoundException(Throwable cause) {
      super(cause);
   }

}

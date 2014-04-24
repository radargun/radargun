package org.radargun.stages.tpcc;

/**
 * @author Sebastiano Peluso &lt;peluso@gsd.inesc-id.pt, peluso@dis.uniroma1.it&gt;
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

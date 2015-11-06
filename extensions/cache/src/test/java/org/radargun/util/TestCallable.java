package org.radargun.util;

import java.util.concurrent.Callable;

/**
 * @author Matej Cimbora
 */
public class TestCallable implements Callable {

   @Override
   public Object call() throws Exception {
      return new Object();
   }
}

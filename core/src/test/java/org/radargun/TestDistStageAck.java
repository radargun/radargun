package org.radargun;

import org.radargun.state.SlaveState;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Roman Macor &lt;rmacor@redhat.com&gt;
 */
@Test
public class TestDistStageAck {

   /**
    * Tests whether setRemoteException handles infinite loops without OOM exception
    */
   @Test(timeOut = 500)
   public void testInfiniteLoopInExceptionCause() {

      Exception exception = new Exception();
      Exception exception1 = new Exception();
      exception1.initCause(exception);
      exception.initCause(exception1);

      DistStageAck distStageAck = new DistStageAck(new SlaveState());
      distStageAck.setRemoteException(exception);
   }
}

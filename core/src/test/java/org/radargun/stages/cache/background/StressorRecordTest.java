package org.radargun.stages.cache.background;

import org.radargun.stages.helpers.Range;
import org.testng.annotations.Test;

import java.util.List;

import static org.radargun.util.ReflectionUtils.*;
import static org.testng.Assert.*;

/**
 * @author Matej Cimbora
 */
// TODO test notifications
@Test
public class StressorRecordTest {

   public void testConfirmationsBasic() throws NoSuchFieldException, IllegalAccessException {
      Range range = new Range(0, 10);
      StressorRecord record = new StressorRecord(0, range);
      assertEquals(record.getCurrentConfirmationTimestamp(), -1);

      List<StressorRecord.StressorConfirmation> confirmations = getClassProperty(StressorRecord.class, record, "confirmations", List.class);
      assertEquals(confirmations.size(), 0);
      record.addConfirmation(0, 1);
      // operationId = 0, confirmationId = 0
      assertEquals(record.getCurrentConfirmationTimestamp(), -1);
      assertEquals(confirmations.size(), 1);

      record.addConfirmation(1, 1);
      // operationId = 0, confirmationId = 1
      assertEquals(record.getCurrentConfirmationTimestamp(), 1);
      assertEquals(confirmations.size(), 2);

      record.checkFinished(0);
      assertEquals(confirmations.size(), 1);

      record.checkFinished(1);
      assertEquals(confirmations.size(), 0);
   }

   public void testConfirmationsDuplicate() throws NoSuchFieldException, IllegalAccessException {
      Range range = new Range(0, 10);
      StressorRecord record = new StressorRecord(0, range);
      record.next();

      record.addConfirmation(10, 10);
      record.addConfirmation(10, 11);
      record.addConfirmation(10, 12);

      assertEquals(getClassProperty(StressorRecord.class, record, "confirmations", List.class).size(), 1);
   }

   public void testConfirmationsShuffled() throws NoSuchFieldException, IllegalAccessException {
      Range range = new Range(0, 10);
      StressorRecord record = new StressorRecord(0, range);
      record.next();

      record.addConfirmation(8, 8);
      record.addConfirmation(10, 10);
      record.addConfirmation(12, 12);
      record.addConfirmation(9, 9);
      record.addConfirmation(11, 11);

      assertEquals(record.getCurrentConfirmationTimestamp(), 12);
      List<StressorRecord.StressorConfirmation> confirmations = getClassProperty(StressorRecord.class, record, "confirmations", List.class);
      assertEquals(confirmations.size(), 5);

      record.checkFinished(12);

      assertEquals(confirmations.size(), 0);
   }

   public void testConfirmationsRemoveMultiple() throws NoSuchFieldException, IllegalAccessException {
      Range range = new Range(0, 10);
      StressorRecord record = new StressorRecord(0, range);
      record.next();

      record.addConfirmation(9, 10);
      record.addConfirmation(10, 11);
      // ignored
      record.addConfirmation(10, 11);
      // ignored
      record.addConfirmation(9, 9);
      record.addConfirmation(11, 12);


      assertEquals(record.getCurrentConfirmationTimestamp(), 12);
      List<StressorRecord.StressorConfirmation> confirmations = getClassProperty(StressorRecord.class, record, "confirmations", List.class);
      assertEquals(confirmations.size(), 3);
      assertEquals(confirmations.get(0), new StressorRecord.StressorConfirmation(9, 10));
      assertEquals(confirmations.get(1), new StressorRecord.StressorConfirmation(10, 11));
      assertEquals(confirmations.get(2), new StressorRecord.StressorConfirmation(11, 12));
   }
}

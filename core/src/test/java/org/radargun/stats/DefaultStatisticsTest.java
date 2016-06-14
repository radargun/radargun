package org.radargun.stats;

import org.radargun.Operation;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

/**
 * @author Matej Cimbora
 */
@Test(sequential = true)
public class DefaultStatisticsTest {

   @AfterTest
   public void cleanup() {
      Operation.clear();
   }

   public void testRegisterOperationsGroup() {
      DefaultStatistics statistics = new DefaultStatistics(new DefaultOperationStats());
      Operation operation1 = Operation.register("testOp1");
      Operation operation2 = Operation.register("testOp2");
      Assert.assertNull(statistics.getOperationsGroup(operation1));
      Assert.assertNull(statistics.getOperationsGroup(operation2));

      statistics.registerOperationsGroup("testGroup1", new HashSet<>(Arrays.asList(operation1)));
      statistics.registerRequest(1, operation1);
      statistics.registerRequest(2, operation2);

      String operationsGroup1 = statistics.getOperationsGroup(operation1);
      Assert.assertEquals("testGroup1", operationsGroup1);
      Assert.assertNull(statistics.getOperationsGroup(operation2));

      statistics.registerOperationsGroup("testGroup2", new HashSet<>(Arrays.asList(operation2)));

      operationsGroup1 = statistics.getOperationsGroup(operation1);
      String operationsGroup2 = statistics.getOperationsGroup(operation2);
      Assert.assertEquals("testGroup1", operationsGroup1);
      Assert.assertEquals("testGroup2", operationsGroup2);
   }

   @Test(expectedExceptions = IllegalArgumentException.class)
   public void testRegisterDuplicateOperationsGroup() {
      DefaultStatistics statistics = new DefaultStatistics(new DefaultOperationStats());
      Operation operation1 = Operation.register("testOp1");
      Operation operation2 = Operation.register("testOp2");

      statistics.registerOperationsGroup("testGroup1", new HashSet<>(Arrays.asList(operation1)));
      statistics.registerOperationsGroup("testGroup1", new HashSet<>(Arrays.asList(operation2)));
   }

   @Test(expectedExceptions = IllegalArgumentException.class)
   public void testRegisterDuplicateOperation() {
      DefaultStatistics statistics = new DefaultStatistics(new DefaultOperationStats());
      Operation operation1 = Operation.register("testOp1");

      statistics.registerOperationsGroup("testGroup1", new HashSet<>(Arrays.asList(operation1)));
      statistics.registerOperationsGroup("testGroup2", new HashSet<>(Arrays.asList(operation1)));
   }

   public void testGetOperationStatsForGroups() {
      DefaultStatistics statistics = new DefaultStatistics(new DefaultOperationStats());
      Operation operation1 = Operation.register("testOp1");
      Operation operation2 = Operation.register("testOp2");

      Map<String, OperationStats> operationStatsForGroups = statistics.getOperationStatsForGroups();
      Assert.assertNotNull(operationStatsForGroups);
      Assert.assertEquals(operationStatsForGroups.size(), 0);

      statistics.registerOperationsGroup("testGroup1", new HashSet<>(Arrays.asList(operation1)));
      statistics.registerRequest(1l, operation1);
      statistics.registerRequest(2l, operation2);

      operationStatsForGroups = statistics.getOperationStatsForGroups();
      Assert.assertNotNull(operationStatsForGroups);
      Assert.assertEquals(operationStatsForGroups.size(), 1);
      Assert.assertTrue(operationStatsForGroups.containsKey("testGroup1"));

      statistics.registerOperationsGroup("testGroup2", new HashSet<>(Arrays.asList(operation2)));

      operationStatsForGroups = statistics.getOperationStatsForGroups();
      Assert.assertNotNull(operationStatsForGroups);
      Assert.assertEquals(operationStatsForGroups.size(), 2);
      Assert.assertTrue(operationStatsForGroups.containsKey("testGroup1"));
      Assert.assertTrue(operationStatsForGroups.containsKey("testGroup2"));
   }

}

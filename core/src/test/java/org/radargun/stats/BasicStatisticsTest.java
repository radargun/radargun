package org.radargun.stats;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.radargun.Operation;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNull;

/**
 * @author Matej Cimbora
 * @author Roman Macor (rmacor@redhat.com)
 */
@Test(sequential = true)
public class BasicStatisticsTest {

   @AfterTest
   public void cleanup() {
      Operation.clear();
   }

   public void testRegisterOperationsGroup() {
      BasicStatistics statistics = new BasicStatistics(new BasicOperationStats());
      Operation operation1 = Operation.register("testOp1");
      Operation operation2 = Operation.register("testOp2");
      assertNull(statistics.getOperationsGroup(operation1));
      assertNull(statistics.getOperationsGroup(operation2));

      statistics.registerOperationsGroup("testGroup1", new HashSet<>(Arrays.asList(operation1)));
      statistics.record(new Request(statistics), operation1);
      statistics.record(new Request(statistics), operation2);

      String operationsGroup1 = statistics.getOperationsGroup(operation1);
      Assert.assertEquals("testGroup1", operationsGroup1);
      assertNull(statistics.getOperationsGroup(operation2));

      statistics.registerOperationsGroup("testGroup2", new HashSet<>(Arrays.asList(operation2)));

      operationsGroup1 = statistics.getOperationsGroup(operation1);
      String operationsGroup2 = statistics.getOperationsGroup(operation2);
      Assert.assertEquals("testGroup1", operationsGroup1);
      Assert.assertEquals("testGroup2", operationsGroup2);
   }

   public void testGetOperationStatsForGroups() {
      BasicStatistics statistics = new BasicStatistics(new BasicOperationStats());
      Operation operation1 = Operation.register("testOp1");
      Operation operation2 = Operation.register("testOp2");

      List<Map<String, OperationStats>> operationStatsForGroups = statistics.getOperationStatsForGroups();
      Assert.assertNotNull(operationStatsForGroups);
      Assert.assertEquals(operationStatsForGroups.get(0).size(), 0);

      statistics.registerOperationsGroup("testGroup1", new HashSet<>(Arrays.asList(operation1)));
      statistics.record(new Request(statistics), operation1);
      statistics.record(new Request(statistics), operation2);

      operationStatsForGroups = statistics.getOperationStatsForGroups();
      Assert.assertNotNull(operationStatsForGroups);
      Assert.assertEquals(operationStatsForGroups.size(), 1);
      Assert.assertTrue(operationStatsForGroups.stream().anyMatch(m -> m.containsKey("testGroup1")));

      statistics.registerOperationsGroup("testGroup2", new HashSet<>(Arrays.asList(operation2)));

      operationStatsForGroups = statistics.getOperationStatsForGroups();
      Assert.assertNotNull(operationStatsForGroups);
      Assert.assertEquals(operationStatsForGroups.get(0).size(), 2);
      Assert.assertTrue(operationStatsForGroups.stream().anyMatch(m -> m.containsKey("testGroup1")));
      Assert.assertTrue(operationStatsForGroups.stream().anyMatch(m -> m.containsKey("testGroup2")));
   }
}

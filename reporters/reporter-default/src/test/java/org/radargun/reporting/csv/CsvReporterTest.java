package org.radargun.reporting.csv;

import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.radargun.Operation;
import org.radargun.config.Cluster;
import org.radargun.config.Configuration;
import org.radargun.reporting.Report;
import org.radargun.reporting.Timeline;
import org.radargun.stats.AllRecordingOperationStats;
import org.radargun.stats.DataOperationStats;
import org.radargun.stats.BasicStatistics;
import org.radargun.stats.Request;
import org.radargun.stats.Statistics;
import org.radargun.utils.TimeService;
import org.radargun.utils.Utils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Matej Cimbora
 */
@Test
@PowerMockIgnore( {"javax.management.*"})
@PrepareForTest(TimeService.class)
public class CsvReporterTest {

   public void testReporterOutput() throws IOException {
      Path tempDirectory = null;
      try {
         tempDirectory = Files.createTempDirectory("HtmlReporterTest_testReporterOutput");
         Configuration configuration1 = new Configuration("conf1");

         Cluster cluster1 = new Cluster();
         cluster1.setSize(2);

         Cluster cluster2 = new Cluster();
         cluster2.setSize(3);

         Timeline timeline1 = new Timeline(0);
         timeline1.addValue(Timeline.Category.sysCategory("event1"), new Timeline.Value(0, 1));
         timeline1.addValue(Timeline.Category.sysCategory("event1"), new Timeline.Value(1000, 2));
         timeline1.addValue(Timeline.Category.sysCategory("event2"), new Timeline.Value(0, 1));
         timeline1.addValue(Timeline.Category.sysCategory("event2"), new Timeline.Value(1000, 2));
         Timeline timeline2 = new Timeline(1);
         timeline2.addValue(Timeline.Category.sysCategory("event1"), new Timeline.Value(0, 1));
         timeline2.addValue(Timeline.Category.sysCategory("event1"), new Timeline.Value(1000, 2));
         timeline2.addValue(Timeline.Category.sysCategory("event2"), new Timeline.Value(0, 1));
         timeline2.addValue(Timeline.Category.sysCategory("event2"), new Timeline.Value(1000, 2));
         Timeline timeline3 = new Timeline(2);
         timeline3.addValue(Timeline.Category.sysCategory("event1"), new Timeline.Value(0, 1));
         timeline3.addValue(Timeline.Category.sysCategory("event1"), new Timeline.Value(1000, 2));
         timeline3.addValue(Timeline.Category.sysCategory("event2"), new Timeline.Value(0, 1));
         timeline3.addValue(Timeline.Category.sysCategory("event2"), new Timeline.Value(1000, 2));


         Operation operation1 = Operation.register("op1");
         Operation operation2 = Operation.register("op2");
         Operation operation3 = Operation.register("op3");

         BasicStatistics basicStatistics1 = new BasicStatistics(new AllRecordingOperationStats());
         basicStatistics1.setBegin(0);
         fakeRequest(basicStatistics1, 10, operation1);
         fakeRequest(basicStatistics1, 20, operation1);
         fakeRequest(basicStatistics1, 30, operation1);
         fakeRequest(basicStatistics1, 100, operation2);
         fakeRequest(basicStatistics1, 200, operation2);
         fakeRequest(basicStatistics1, 300, operation3);
         fakeRequestError(basicStatistics1, 300,operation3);
         basicStatistics1.setEnd(1001);

         DataOperationStats dos = new DataOperationStats();
         BasicStatistics basicStatistics2 = new BasicStatistics();
         basicStatistics2.setBegin(0);
         fakeRequest(basicStatistics2, 100, operation1);
         fakeRequest(basicStatistics2, 200, operation1);
         dos.setTotalBytes(200l);
         basicStatistics2.setEnd(1001);

         Report report1 = new Report(configuration1, cluster1);
         report1.addStage("stage1");
         report1.addStage("stage2");
         Report.Test test1 = report1.createTest("test1", "it1", true);
         Map<Integer, Report.SlaveResult> slaveResults = new HashMap<>();
         slaveResults.put(0, new Report.SlaveResult("10", false));
         slaveResults.put(1, new Report.SlaveResult("20", false));
         test1.addResult(0, new Report.TestResult("test1", slaveResults, "30", false));
         test1.addResult(1, new Report.TestResult("test1", slaveResults, "30", false));
         test1.addStatistics(0, 0, Arrays.asList((Statistics) basicStatistics1));
         test1.addStatistics(0, 1, Arrays.asList((Statistics) basicStatistics1));
         test1.addStatistics(1, 0, Arrays.asList((Statistics) basicStatistics1));
         test1.addStatistics(1, 1, Arrays.asList((Statistics) basicStatistics1));
         report1.addTimelines(Arrays.asList(timeline1, timeline2));

         Report report2 = new Report(configuration1, cluster2);
         report2.addStage("stage1");
         report2.addStage("stage2");
         Report.Test test2 = report2.createTest("test1", "it1", true);
         slaveResults = new HashMap<>();
         slaveResults.put(0, new Report.SlaveResult("10", false));
         slaveResults.put(1, new Report.SlaveResult("20", false));
         slaveResults.put(2, new Report.SlaveResult("30", false));
         test2.addResult(0, new Report.TestResult("test1", slaveResults, "60", false));
         test2.addResult(1, new Report.TestResult("test1", slaveResults, "60", false));
         test2.addStatistics(0, 0, Arrays.asList((Statistics) basicStatistics1));
         test2.addStatistics(0, 1, Arrays.asList((Statistics) basicStatistics1));
         test2.addStatistics(0, 2, Arrays.asList((Statistics) basicStatistics1));
         test2.addStatistics(1, 0, Arrays.asList((Statistics) basicStatistics1));
         test2.addStatistics(1, 1, Arrays.asList((Statistics) basicStatistics1));
         test2.addStatistics(1, 2, Arrays.asList((Statistics) basicStatistics1));
         report2.addTimelines(Arrays.asList(timeline1, timeline2, timeline3));

         CsvReporter csvReporter = new CsvReporter();
         Utils.setField(CsvReporter.class, "targetDir", csvReporter, tempDirectory.toString());

         csvReporter.run(Arrays.asList(report1, report2));

         List<String> lines = Files.readAllLines(Paths.get(tempDirectory.toString(), "test1_conf1_default_2.csv"));
         // header (1 line) + 2 iterations (4 lines) + summary (2 lines)
         Assert.assertEquals(lines.size(), 7);
         Assert.assertEquals(lines.get(0), "SlaveIndex;Iteration;Period;ThreadCount;" +
            "op1.Errors;op1.RTM_95.0;op1.RTM_99.0;op1.Requests;op1.ResponseTimeDeviation;op1.ResponseTimeMax;op1.ResponseTimeMean;op1.ThroughputGross;op1.ThroughputNet;" +
            "op2.Errors;op2.RTM_95.0;op2.RTM_99.0;op2.Requests;op2.ResponseTimeDeviation;op2.ResponseTimeMax;op2.ResponseTimeMean;op2.ThroughputGross;op2.ThroughputNet;" +
            "op3.Errors;op3.RTM_95.0;op3.RTM_99.0;op3.Requests;op3.ResponseTimeDeviation;op3.ResponseTimeMax;op3.ResponseTimeMean;op3.ThroughputGross;op3.ThroughputNet;");
         Assert.assertEquals(lines.get(1), "0;0;1001;1;0;30.0;30.0;3;10.0;30;20.0;3.0;3.0;0;200.0;200.0;2;70.71;200;150.0;2.0;2.0;0;300.0;300.0;2;0.0;300;300.0;2.0;2.0;");
         Assert.assertEquals(lines.get(2), "1;0;1001;1;0;30.0;30.0;3;10.0;30;20.0;3.0;3.0;0;200.0;200.0;2;70.71;200;150.0;2.0;2.0;0;300.0;300.0;2;0.0;300;300.0;2.0;2.0;");
         Assert.assertEquals(lines.get(3), "TOTAL;0;1001;2;0;30.0;30.0;6;8.94;30;20.0;6.0;6.0;0;200.0;200.0;4;57.74;200;150.0;4.0;4.0;0;300.0;300.0;4;0.0;300;300.0;4.0;4.0;");
         Assert.assertEquals(lines.get(4), "0;1;1001;1;0;30.0;30.0;3;10.0;30;20.0;3.0;3.0;0;200.0;200.0;2;70.71;200;150.0;2.0;2.0;0;300.0;300.0;2;0.0;300;300.0;2.0;2.0;");
         Assert.assertEquals(lines.get(5), "1;1;1001;1;0;30.0;30.0;3;10.0;30;20.0;3.0;3.0;0;200.0;200.0;2;70.71;200;150.0;2.0;2.0;0;300.0;300.0;2;0.0;300;300.0;2.0;2.0;");
         Assert.assertEquals(lines.get(6), "TOTAL;1;1001;2;0;30.0;30.0;6;8.94;30;20.0;6.0;6.0;0;200.0;200.0;4;57.74;200;150.0;4.0;4.0;0;300.0;300.0;4;0.0;300;300.0;4.0;4.0;");

         lines = Files.readAllLines(Paths.get(tempDirectory.toString(), "test1_conf1_default_3.csv"));
         // header (1 line) + 2 iterations (6 lines) + summary (2 lines)
         Assert.assertEquals(lines.size(), 9);
         Assert.assertEquals(lines.get(0), "SlaveIndex;Iteration;Period;ThreadCount;" +
            "op1.Errors;op1.RTM_95.0;op1.RTM_99.0;op1.Requests;op1.ResponseTimeDeviation;op1.ResponseTimeMax;op1.ResponseTimeMean;op1.ThroughputGross;op1.ThroughputNet;" +
            "op2.Errors;op2.RTM_95.0;op2.RTM_99.0;op2.Requests;op2.ResponseTimeDeviation;op2.ResponseTimeMax;op2.ResponseTimeMean;op2.ThroughputGross;op2.ThroughputNet;" +
            "op3.Errors;op3.RTM_95.0;op3.RTM_99.0;op3.Requests;op3.ResponseTimeDeviation;op3.ResponseTimeMax;op3.ResponseTimeMean;op3.ThroughputGross;op3.ThroughputNet;");
         Assert.assertEquals(lines.get(1), "0;0;1001;1;0;30.0;30.0;3;10.0;30;20.0;3.0;3.0;0;200.0;200.0;2;70.71;200;150.0;2.0;2.0;0;300.0;300.0;2;0.0;300;300.0;2.0;2.0;");
         Assert.assertEquals(lines.get(2), "1;0;1001;1;0;30.0;30.0;3;10.0;30;20.0;3.0;3.0;0;200.0;200.0;2;70.71;200;150.0;2.0;2.0;0;300.0;300.0;2;0.0;300;300.0;2.0;2.0;");
         Assert.assertEquals(lines.get(3), "2;0;1001;1;0;30.0;30.0;3;10.0;30;20.0;3.0;3.0;0;200.0;200.0;2;70.71;200;150.0;2.0;2.0;0;300.0;300.0;2;0.0;300;300.0;2.0;2.0;");
         Assert.assertEquals(lines.get(4), "TOTAL;0;1001;3;0;30.0;30.0;9;8.66;30;20.0;9.0;9.0;0;200.0;200.0;6;54.77;200;150.0;6.0;6.0;0;300.0;300.0;6;0.0;300;300.0;6.0;6.0;");
         Assert.assertEquals(lines.get(5), "0;1;1001;1;0;30.0;30.0;3;10.0;30;20.0;3.0;3.0;0;200.0;200.0;2;70.71;200;150.0;2.0;2.0;0;300.0;300.0;2;0.0;300;300.0;2.0;2.0;");
         Assert.assertEquals(lines.get(6), "1;1;1001;1;0;30.0;30.0;3;10.0;30;20.0;3.0;3.0;0;200.0;200.0;2;70.71;200;150.0;2.0;2.0;0;300.0;300.0;2;0.0;300;300.0;2.0;2.0;");
         Assert.assertEquals(lines.get(7), "2;1;1001;1;0;30.0;30.0;3;10.0;30;20.0;3.0;3.0;0;200.0;200.0;2;70.71;200;150.0;2.0;2.0;0;300.0;300.0;2;0.0;300;300.0;2.0;2.0;");
         Assert.assertEquals(lines.get(8), "TOTAL;1;1001;3;0;30.0;30.0;9;8.66;30;20.0;9.0;9.0;0;200.0;200.0;6;54.77;200;150.0;6.0;6.0;0;300.0;300.0;6;0.0;300;300.0;6.0;6.0;");

         lines = Files.readAllLines(Paths.get(tempDirectory.toString(), "timeline_conf1_default_2_event1.csv"));
         Assert.assertEquals(lines.size(), 3);
         Assert.assertEquals(lines.get(0), "Timestamp;Slave 0;Slave 1;");
         Assert.assertEquals(lines.get(1), "0;1;1;");
         Assert.assertEquals(lines.get(2), "1000;2;2;");

         lines = Files.readAllLines(Paths.get(tempDirectory.toString(), "timeline_conf1_default_2_event2.csv"));
         Assert.assertEquals(lines.size(), 3);
         Assert.assertEquals(lines.get(0), "Timestamp;Slave 0;Slave 1;");
         Assert.assertEquals(lines.get(1), "0;1;1;");
         Assert.assertEquals(lines.get(2), "1000;2;2;");

         lines = Files.readAllLines(Paths.get(tempDirectory.toString(), "timeline_conf1_default_3_event1.csv"));
         Assert.assertEquals(lines.size(), 3);
         Assert.assertEquals(lines.get(0), "Timestamp;Slave 0;Slave 1;Slave 2;");
         Assert.assertEquals(lines.get(1), "0;1;1;1;");
         Assert.assertEquals(lines.get(2), "1000;2;2;2;");

         lines = Files.readAllLines(Paths.get(tempDirectory.toString(), "timeline_conf1_default_3_event2.csv"));
         Assert.assertEquals(lines.size(), 3);
         Assert.assertEquals(lines.get(0), "Timestamp;Slave 0;Slave 1;Slave 2;");
         Assert.assertEquals(lines.get(1), "0;1;1;1;");
         Assert.assertEquals(lines.get(2), "1000;2;2;2;");
      } catch (Exception e) {
         if (tempDirectory != null) {
            Utils.deleteDirectory(tempDirectory.toFile());
         }
      }
   }

   private void fakeRequest(BasicStatistics stats, long duration, Operation operation) {
      PowerMockito.when(TimeService.nanoTime()).thenReturn(0L);
      Request request = stats.startRequest();
      PowerMockito.when(TimeService.nanoTime()).thenReturn(duration);
      request.succeeded(operation);
   }

   private void fakeRequestError(BasicStatistics stats, long duration, Operation operation) {
      PowerMockito.when(TimeService.nanoTime()).thenReturn(0L);
      Request request = stats.startRequest();
      PowerMockito.when(TimeService.nanoTime()).thenReturn(duration);
      request.failed(operation);
   }
}

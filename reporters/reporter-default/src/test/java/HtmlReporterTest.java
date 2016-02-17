import org.radargun.Operation;
import org.radargun.config.Cluster;
import org.radargun.config.Configuration;
import org.radargun.reporting.Report;
import org.radargun.reporting.Timeline;
import org.radargun.reporting.html.HtmlReporter;
import org.radargun.stats.DefaultOperationStats;
import org.radargun.stats.DefaultStatistics;
import org.radargun.stats.Statistics;
import org.radargun.utils.Utils;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Matej Cimbora
 */
public class HtmlReporterTest {

   /**
    * Just a basic test with random values, useful for debugging purposes. Make sure to comment out
    * 'Utils.deleteDirectory(...)' line when in a need to verify the output manually. By default the content
    * of temporary directory is cleared after each test run.
    */
   @Test
   public void testHtmlReporter() throws IOException {
      Path tempDirectory = null;
      try {
         HtmlReporter htmlReporter = new HtmlReporter();
         tempDirectory = Files.createTempDirectory("HtmlReporterTest_testHtmlReporter");
         Utils.setField(HtmlReporter.class, "targetDir", htmlReporter, tempDirectory.toString());

         Configuration configuration1 = new Configuration("test1");
         Configuration configuration2 = new Configuration("test2");

         Cluster cluster1 = new Cluster();
         cluster1.setSize(2);

         Cluster cluster2 = new Cluster();
         cluster2.setSize(3);

         Timeline timeline1 = new Timeline(0);
         timeline1.addValue("event1", new Timeline.Value(0, 1));
         timeline1.addValue("event1", new Timeline.Value(1000, 2));
         timeline1.addValue("event2", new Timeline.Value(0, 1));
         timeline1.addValue("event2", new Timeline.Value(1000, 2));
         Timeline timeline2 = new Timeline(1);
         timeline2.addValue("event1", new Timeline.Value(0, 1));
         timeline2.addValue("event1", new Timeline.Value(1000, 2));
         timeline2.addValue("event2", new Timeline.Value(0, 1));
         timeline2.addValue("event2", new Timeline.Value(1000, 2));
         Timeline timeline3 = new Timeline(2);
         timeline3.addValue("event1", new Timeline.Value(0, 1));
         timeline3.addValue("event1", new Timeline.Value(1000, 2));
         timeline3.addValue("event2", new Timeline.Value(0, 1));
         timeline3.addValue("event2", new Timeline.Value(1000, 2));


         Operation operation1 = Operation.register("op1");
         Operation operation2 = Operation.register("op2");
         Operation operation3 = Operation.register("op3");

         DefaultStatistics defaultStatistics1 = new DefaultStatistics(new DefaultOperationStats());
         defaultStatistics1.setBegin(0);
         defaultStatistics1.registerRequest(10, operation1);
         defaultStatistics1.registerRequest(20, operation1);
         defaultStatistics1.registerRequest(30, operation1);
         defaultStatistics1.registerRequest(100, operation2);
         defaultStatistics1.registerRequest(200, operation2);
         defaultStatistics1.registerRequest(300, operation3);
         defaultStatistics1.registerError(300, operation3);
         defaultStatistics1.setEnd(1001);

         Report report1 = new Report(configuration1, cluster1);
         report1.addStage("stage1");
         report1.addStage("stage2");
         Report.Test test1 = report1.createTest("test1", "it1", true);
         Map<Integer, Report.SlaveResult> slaveResults = new HashMap<>();
         slaveResults.put(0, new Report.SlaveResult("10", false));
         slaveResults.put(1, new Report.SlaveResult("20", false));
         test1.addResult(0, new Report.TestResult("test1", slaveResults, "30", false));
         test1.addStatistics(0, 0, Arrays.asList((Statistics) defaultStatistics1));
         test1.addStatistics(0, 1, Arrays.asList((Statistics) defaultStatistics1));
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
         test2.addStatistics(0, 0, Arrays.asList((Statistics) defaultStatistics1));
         test2.addStatistics(0, 1, Arrays.asList((Statistics) defaultStatistics1));
         test2.addStatistics(0, 2, Arrays.asList((Statistics) defaultStatistics1));
         report2.addTimelines(Arrays.asList(timeline1, timeline2, timeline3));

         Report report3 = new Report(configuration2, cluster1);
         report3.addStage("stage1");
         report3.addStage("stage2");
         Report.Test test3 = report3.createTest("test1", "it1", true);
         slaveResults = new HashMap<>();
         slaveResults.put(0, new Report.SlaveResult("10", false));
         slaveResults.put(1, new Report.SlaveResult("20", false));
         test3.addResult(0, new Report.TestResult("test1", slaveResults, "30", false));
         test3.addStatistics(0, 0, Arrays.asList((Statistics) defaultStatistics1));
         test3.addStatistics(0, 1, Arrays.asList((Statistics) defaultStatistics1));
         report3.addTimelines(Arrays.asList(timeline1, timeline2));

         Report report4 = new Report(configuration2, cluster2);
         report4.addStage("stage1");
         report4.addStage("stage2");
         Report.Test test4 = report4.createTest("test1", "it1", true);
         slaveResults = new HashMap<>();
         slaveResults.put(0, new Report.SlaveResult("10", false));
         slaveResults.put(1, new Report.SlaveResult("20", false));
         slaveResults.put(2, new Report.SlaveResult("30", false));
         test4.addResult(0, new Report.TestResult("test1", slaveResults, "60", false));
         test4.addStatistics(0, 0, Arrays.asList((Statistics) defaultStatistics1));
         test4.addStatistics(0, 1, Arrays.asList((Statistics) defaultStatistics1));
         test4.addStatistics(0, 2, Arrays.asList((Statistics) defaultStatistics1));
         report4.addTimelines(Arrays.asList(timeline1, timeline2, timeline3));

         htmlReporter.run(Arrays.asList(report1, report2, report3, report4));
      } finally {
         // comment out when debugging
         Utils.deleteDirectory(tempDirectory.toFile());
      }
   }


}
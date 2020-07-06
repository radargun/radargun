package org.radargun.stages.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.traits.ContinuousQuery;
import org.radargun.traits.Lifecycle;
import org.radargun.util.QueryStageRunner;
import org.radargun.util.QueryTraitRepository;
import org.radargun.utils.Utils;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Matej Cimbora
 */
@Test(timeOut = 30000)
public class ContinuousQueryStageTest {

   public void smokeTest() throws Exception {
      QueryStageRunner stageRunner = new QueryStageRunner(1);

      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();
      ContinuousQueryStage continuousQueryStage = new ContinuousQueryStage();

      QueryConfiguration queryConfiguration = new QueryConfiguration();
      queryConfiguration.clazz = "java.lang.Integer";

      Condition.Eq eqCondition = new Condition.Eq();
      eqCondition.value = new Object();
      eqCondition.path = "path";

      Condition.Lt ltCondition = new Condition.Lt();
      ltCondition.value = Long.valueOf(1);
      ltCondition.path = "path";

      Condition.Any anyCondition = new Condition.Any();
      Utils.setField(Condition.Any.class, "subs", anyCondition, Arrays.asList(eqCondition, ltCondition));

      List<Condition> conditions = Arrays.asList(anyCondition);

      queryConfiguration.conditions = conditions;

      OrderBy orderBy = new OrderBy("attribute", true);
      queryConfiguration.orderBy = Arrays.asList(orderBy);

      queryConfiguration.projection = new String[] {"attribute1", "attribute2"};

      continuousQueryStage.query = queryConfiguration;
      continuousQueryStage.cacheName = "test";

      QueryTraitRepository.ContinuousQuery continuousQuery = (QueryTraitRepository.ContinuousQuery) stageRunner.getTraitImpl(ContinuousQuery.class);
      Assert.assertNull(continuousQuery.getCacheCqMap().get("test"));

      List<DistStageAck> acks = new ArrayList<>(1);

      acks.add(stageRunner.executeOnWorker(continuousQueryStage, 0));

      Assert.assertNotNull(continuousQuery.getCacheCqMap().get("test"));
      Assert.assertEquals(stageRunner.processAckOnMain(continuousQueryStage, acks), StageResult.SUCCESS);

      continuousQueryStage.remove = true;

      acks = new ArrayList<>(1);

      acks.add(stageRunner.executeOnWorker(continuousQueryStage, 0));

      Assert.assertNull(continuousQuery.getCacheCqMap().get("test"));
      Assert.assertEquals(stageRunner.processAckOnMain(continuousQueryStage, acks), StageResult.SUCCESS);
   }
}

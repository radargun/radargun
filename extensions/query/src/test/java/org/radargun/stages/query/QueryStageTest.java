package org.radargun.stages.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.stages.test.TransactionMode;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.Queryable;
import org.radargun.util.QueryStageRunner;
import org.radargun.util.QueryTraitRepository;
import org.radargun.utils.Utils;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Matej Cimbora
 */
@Test(timeOut = 30000)
public class QueryStageTest {

   public void smokeTest() throws Exception {
      QueryStageRunner stageRunner = new QueryStageRunner(1);

      Lifecycle lifecycle = stageRunner.getTraitImpl(Lifecycle.class);
      lifecycle.start();
      QueryStage queryStage = new QueryStage();
      queryStage.totalThreads = 5;
      queryStage.duration = 3000;
      queryStage.useTransactions = TransactionMode.NEVER;

      QueryConfiguration queryConfiguration = new QueryConfiguration();
      Utils.setField(QueryConfiguration.class, "clazz", queryConfiguration, "java.lang.Integer");

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

      Utils.setField(QueryStage.class, "query", queryStage, queryConfiguration);

      QueryTraitRepository.Queryable queryable = (QueryTraitRepository.Queryable) stageRunner.getTraitImpl(Queryable.class);
      ConcurrentHashMap cache = new ConcurrentHashMap();
      queryable.setCache(cache);

      IntStream.range(0, 100).forEach(i -> cache.put(i, i));

      Assert.assertEquals(cache.size(), 100);

      List<DistStageAck> acks = new ArrayList<>(1);

      acks.add(stageRunner.executeOnWorker(queryStage, 0));

      Assert.assertEquals(stageRunner.processAckOnMain(queryStage, acks), StageResult.SUCCESS);
   }
}

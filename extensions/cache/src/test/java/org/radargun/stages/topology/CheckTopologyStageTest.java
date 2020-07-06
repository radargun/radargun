package org.radargun.stages.topology;

import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.testng.PowerMockTestCase;
import org.radargun.DistStageAck;
import org.radargun.logging.Log;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.WorkerState;
import org.radargun.traits.TopologyHistory;
import org.radargun.utils.TimeService;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static org.radargun.traits.TopologyHistory.HistoryType;
import static org.radargun.util.ReflectionUtils.setClassProperty;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Matej Cimbora
 */
@Test
@PowerMockIgnore({"javax.management.*"})
@PrepareForTest(TimeService.class)
public class CheckTopologyStageTest extends PowerMockTestCase {

   @Test(timeOut = 10_000)
   public void testCheckTopologyChanges() throws Exception {
      testCheckEvent(EnumSet.of(HistoryType.TOPOLOGY));
   }

   @Test(timeOut = 10_000)
   public void testCheckRehashChanges() throws Exception {
      testCheckEvent(EnumSet.of(HistoryType.REHASH));
   }

   @Test(timeOut = 10_000)
   public void testCheckCacheStatusChanges() throws Exception {
      testCheckEvent(EnumSet.of(HistoryType.CACHE_STATUS));
   }

   @Test(timeOut = 10_000)
   public void testCheckAllChanges() throws Exception {
      testCheckEvent(EnumSet.allOf(HistoryType.class));
   }

   private void testCheckEvent(EnumSet<HistoryType> checkType) throws NoSuchFieldException, IllegalAccessException {
      CheckTopologyStage stage = initStage(checkType, 10, true);

      TopologyHistory topologyHistory = mock(TopologyHistory.class);
      setClassProperty(CheckTopologyStage.class, stage, "topologyHistory", topologyHistory);

      doReturn(true).when(stage).isServiceRunning();

      TopologyHistory.Event unfinishedEvent = mock(TopologyHistory.Event.class);
      doReturn(new Date(1)).when(unfinishedEvent).getTime();
      doReturn(TopologyHistory.Event.EventType.START).when(unfinishedEvent).getType();

      TopologyHistory.Event finishedEvent = mock(TopologyHistory.Event.class);
      doReturn(new Date(2)).when(finishedEvent).getTime();
      doReturn(TopologyHistory.Event.EventType.END).when(finishedEvent).getType();

      List<TopologyHistory.Event> topologyEvents = new ArrayList<>(2);
      topologyEvents.add(unfinishedEvent);
      topologyEvents.add(finishedEvent);

      if (checkType.containsAll(Arrays.asList(HistoryType.values()))) {
         doReturn(topologyEvents).when(topologyHistory).getTopologyChangeHistory(anyString());
         doReturn(topologyEvents).when(topologyHistory).getRehashHistory(anyString());
         doReturn(topologyEvents).when(topologyHistory).getCacheStatusChangeHistory(anyString());
      } else if (checkType.contains(HistoryType.TOPOLOGY)) {
         doReturn(topologyEvents).when(topologyHistory).getTopologyChangeHistory(anyString());
      } else if (checkType.contains(HistoryType.REHASH)) {
         doReturn(topologyEvents).when(topologyHistory).getRehashHistory(anyString());
      } else if (checkType.contains(HistoryType.CACHE_STATUS)) {
         doReturn(topologyEvents).when(topologyHistory).getCacheStatusChangeHistory(anyString());
      }

      PowerMockito.mockStatic(TimeService.class);
      PowerMockito.when(TimeService.currentTimeMillis()).thenReturn(10l);

      DistStageAck stageResult = stage.executeOnWorker();
      assertFalse(stageResult.isError());

      PowerMockito.when(TimeService.currentTimeMillis()).thenReturn(13l);
      stageResult = stage.executeOnWorker();
      assertTrue(stageResult.isError());
   }

   private CheckTopologyStage initStage(EnumSet<HistoryType> checkEvents, long checkPeriod, boolean changed) throws NoSuchFieldException, IllegalAccessException {
      CheckTopologyStage stage = spy(new CheckTopologyStage());
      stage.initOnWorker(mock(WorkerState.class));

      Log log = mock(Log.class);
      setClassProperty(AbstractDistStage.class, stage, "log", log);
      stage.checkEvents = checkEvents;
      stage.period = checkPeriod;
      stage.changed = changed;
      return stage;
   }
}

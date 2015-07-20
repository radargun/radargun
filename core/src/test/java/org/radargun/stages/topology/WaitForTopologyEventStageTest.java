package org.radargun.stages.topology;

import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.testng.PowerMockTestCase;
import org.radargun.DistStageAck;
import org.radargun.logging.Log;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.SlaveState;
import org.radargun.traits.TopologyHistory;
import org.radargun.utils.TimeService;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static org.radargun.traits.TopologyHistory.Event.EventType;
import static org.radargun.util.ReflectionUtils.getClassProperty;
import static org.radargun.util.ReflectionUtils.setClassProperty;
import static org.testng.Assert.*;

/**
 * @author Matej Cimbora
 */
@Test
@PowerMockIgnore( {"javax.management.*"})
@PrepareForTest(TimeService.class)
public class WaitForTopologyEventStageTest extends PowerMockTestCase {

   @Test(timeOut = 10_000)
   public void testCheckTopologyChangesStart() throws Exception {
      testCheckEvent(TopologyHistory.HistoryType.TOPOLOGY, EventType.START);
   }

   @Test(timeOut = 10_000)
   public void testCheckTopologyChangesEnd() throws Exception {
      testCheckEvent(TopologyHistory.HistoryType.TOPOLOGY, EventType.END);
   }

   @Test(timeOut = 10_000)
   public void testCheckRehashChangesStart() throws Exception {
      testCheckEvent(TopologyHistory.HistoryType.REHASH, EventType.START);
   }

   @Test(timeOut = 10_000)
   public void testCheckRehashChangesEnd() throws Exception {
      testCheckEvent(TopologyHistory.HistoryType.REHASH, EventType.END);
   }

   @Test(timeOut = 10_000)
   public void testCheckCacheStatusChangesStart() throws Exception {
      testCheckEvent(TopologyHistory.HistoryType.CACHE_STATUS, EventType.START);
   }

   @Test(timeOut = 10_000)
   public void testCheckAllChanges() throws Exception {
      testCheckEvent(TopologyHistory.HistoryType.CACHE_STATUS, EventType.END);
   }

   private void testCheckEvent(TopologyHistory.HistoryType checkType, EventType condition) throws NoSuchFieldException, IllegalAccessException {
      WaitForTopologyEventStage stage = initStage(checkType, condition, true, true, 10);

      TopologyHistory topologyHistory = mock(TopologyHistory.class);
      setClassProperty(WaitForTopologyEventStage.class, stage, "topologyHistory", topologyHistory);

      doReturn(true).when(stage).isServiceRunning();

      TopologyHistory.Event unfinishedEvent1 = mock(TopologyHistory.Event.class);
      doReturn(new Date(1)).when(unfinishedEvent1).getTime();
      doReturn(TopologyHistory.Event.EventType.START).when(unfinishedEvent1).getType();

      TopologyHistory.Event finishedEvent1 = mock(TopologyHistory.Event.class);
      doReturn(new Date(2)).when(finishedEvent1).getTime();
      doReturn(TopologyHistory.Event.EventType.END).when(finishedEvent1).getType();

      TopologyHistory.Event unfinishedEvent2 = mock(TopologyHistory.Event.class);
      doReturn(new Date(3)).when(unfinishedEvent2).getTime();
      doReturn(TopologyHistory.Event.EventType.START).when(unfinishedEvent2).getType();

      TopologyHistory.Event finishedEvent2 = mock(TopologyHistory.Event.class);
      doReturn(new Date(4)).when(finishedEvent2).getTime();
      doReturn(TopologyHistory.Event.EventType.END).when(finishedEvent2).getType();

      TopologyHistory.Event singleEvent1 = mock(TopologyHistory.Event.class);
      doReturn(new Date(5)).when(singleEvent1).getTime();
      doReturn(TopologyHistory.Event.EventType.SINGLE).when(singleEvent1).getType();

      TopologyHistory.Event singleEvent2 = mock(TopologyHistory.Event.class);
      doReturn(new Date(6)).when(singleEvent2).getTime();
      doReturn(TopologyHistory.Event.EventType.SINGLE).when(singleEvent2).getType();

      SlaveState slaveState = getClassProperty(AbstractDistStage.class, stage, "slaveState", SlaveState.class);

      switch (condition) {
         case START : {
            doReturn(unfinishedEvent1).when(slaveState).get(anyString());
            break;
         }
         case END: {
            doReturn(finishedEvent1).when(slaveState).get(anyString());
            break;
         }
         case SINGLE : {
            doReturn(singleEvent1).when(slaveState).get(anyString());
            break;
         }
      }

      List<TopologyHistory.Event> topologyEvents = new ArrayList<>(6);
      topologyEvents.add(unfinishedEvent1);
      topologyEvents.add(unfinishedEvent2);
      topologyEvents.add(finishedEvent1);
      topologyEvents.add(finishedEvent2);
      topologyEvents.add(singleEvent1);
      topologyEvents.add(singleEvent2);

      switch (checkType) {
         case TOPOLOGY: {
            doReturn(topologyEvents).when(topologyHistory).getTopologyChangeHistory(anyString());
            break;
         }
         case REHASH: {
            doReturn(topologyEvents).when(topologyHistory).getRehashHistory(anyString());
            break;
         }
         case CACHE_STATUS: {
            doReturn(topologyEvents).when(topologyHistory).getCacheStatusChangeHistory(anyString());
            break;
         }
      }

      PowerMockito.mockStatic(TimeService.class);
      PowerMockito.when(TimeService.currentTimeMillis()).thenReturn(7l).thenReturn(8l);

      DistStageAck stageResult = stage.executeOnSlave();
      assertFalse(stageResult.isError());

      switch (checkType) {
         case TOPOLOGY: {
            verify(topologyHistory, times(1)).getTopologyChangeHistory(anyString());
            break;
         }
         case REHASH: {
            verify(topologyHistory, times(1)).getRehashHistory(anyString());
            break;
         }
         case CACHE_STATUS: {
            verify(topologyHistory, times(1)).getCacheStatusChangeHistory(anyString());
            break;
         }
      }
   }

   public void testPutOnly() throws NoSuchFieldException, IllegalAccessException {
      WaitForTopologyEventStage stage = initStage(TopologyHistory.HistoryType.TOPOLOGY, EventType.START, true, false, 10);
      SlaveState slaveState = getClassProperty(AbstractDistStage.class, stage, "slaveState", SlaveState.class);

      TopologyHistory topologyHistory = mock(TopologyHistory.class);
      setClassProperty(WaitForTopologyEventStage.class, stage, "topologyHistory", topologyHistory);
      doReturn(true).when(stage).isServiceRunning();

      TopologyHistory.Event unfinishedEvent1 = mock(TopologyHistory.Event.class);
      doReturn(new Date(1)).when(unfinishedEvent1).getTime();
      doReturn(TopologyHistory.Event.EventType.START).when(unfinishedEvent1).getType();

      DistStageAck stageResult = stage.executeOnSlave();
      assertFalse(stageResult.isError());

      verify(slaveState, times(1)).put(anyString(), anyObject());
   }

   private WaitForTopologyEventStage initStage(TopologyHistory.HistoryType checkType, EventType condition,
                                               boolean set, boolean wait, long timeout) throws NoSuchFieldException, IllegalAccessException {
      WaitForTopologyEventStage stage = spy(new WaitForTopologyEventStage());
      stage.initOnSlave(mock(SlaveState.class));

      Log log = mock(Log.class);
      setClassProperty(AbstractDistStage.class, stage, "log", log);
      setClassProperty(WaitForTopologyEventStage.class, stage, "type", checkType);
      setClassProperty(WaitForTopologyEventStage.class, stage, "condition", condition);
      setClassProperty(WaitForTopologyEventStage.class, stage, "set", set);
      setClassProperty(WaitForTopologyEventStage.class, stage, "wait", wait);
      setClassProperty(WaitForTopologyEventStage.class, stage, "timeout", timeout);
      return stage;
   }
}

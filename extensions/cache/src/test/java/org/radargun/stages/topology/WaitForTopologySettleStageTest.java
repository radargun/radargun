package org.radargun.stages.topology;

import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.testng.PowerMockTestCase;
import org.radargun.DistStageAck;
import org.radargun.logging.Log;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.SlaveState;
import org.radargun.traits.Clustered;
import org.radargun.traits.TopologyHistory;
import org.radargun.utils.TimeService;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.radargun.traits.TopologyHistory.HistoryType;
import static org.radargun.traits.TopologyHistory.Event.EventType;
import static org.radargun.util.ReflectionUtils.*;
import static org.testng.Assert.*;

/**
 * @author Matej Cimbora
 */
@Test
@PowerMockIgnore({"javax.management.*"})
@PrepareForTest(TimeService.class)
public class WaitForTopologySettleStageTest extends PowerMockTestCase {

   @Test(timeOut = 10_000)
   public void testExecuteOnSlaveTopologyChanges() throws Exception {
      testExecuteOnSlaveTopologyChanges(EnumSet.of(HistoryType.TOPOLOGY));
   }

   @Test(timeOut = 10_000)
   public void testExecuteOnSlaveRehashChanges() throws Exception {
      testExecuteOnSlaveTopologyChanges(EnumSet.of(HistoryType.REHASH));
   }

   @Test(timeOut = 10_000)
   public void testExecuteOnSlaveCacheStatusChanges() throws Exception {
      testExecuteOnSlaveTopologyChanges(EnumSet.of(HistoryType.CACHE_STATUS));
   }

   @Test(timeOut = 10_000)
   public void testExecuteOnSlaveMembershipChanges() throws NoSuchFieldException, IllegalAccessException {
      WaitForTopologySettleStage stage = initStage(EnumSet.noneOf(HistoryType.class), true);
      setClassProperty(WaitForTopologySettleStage.class, stage, "period", 10l);
      Clustered clustered = mock(Clustered.class);
      setClassProperty(WaitForTopologySettleStage.class, stage, "clustered", clustered);

      doReturn(true).when(stage).isServiceRunning();

      Clustered.Membership membershipEvent1 = new Clustered.Membership(new Date(1), mock(List.class));
      Clustered.Membership membershipEvent2 = new Clustered.Membership(new Date(2), mock(List.class));
      List<Clustered.Membership> membershipList = new ArrayList<>(2);
      membershipList.add(membershipEvent1);
      membershipList.add(membershipEvent2);
      doReturn(membershipList).when(clustered).getMembershipHistory();

      PowerMockito.mockStatic(TimeService.class);
      PowerMockito.when(TimeService.currentTimeMillis()).thenReturn(10l).thenReturn(10l).thenReturn(13l);

      DistStageAck distStageAck = stage.executeOnSlave();
      assertFalse(distStageAck.isError());

      verify(clustered, times(2)).getMembershipHistory();
   }

   private void testExecuteOnSlaveTopologyChanges(EnumSet<TopologyHistory.HistoryType> checkEvents) throws Exception {
      WaitForTopologySettleStage stage = initStage(checkEvents, false);
      setClassProperty(WaitForTopologySettleStage.class, stage, "period", 0l);
      TopologyHistory topologyHistory = mock(TopologyHistory.class);
      setClassProperty(WaitForTopologySettleStage.class, stage, "history", topologyHistory);

      doReturn(true).when(stage).isServiceRunning();

      TopologyHistory.Event unfinishedEvent1 = mock(TopologyHistory.Event.class);
      doReturn(new Date(1)).when(unfinishedEvent1).getTime();
      doReturn(EventType.START).when(unfinishedEvent1).getType();

      TopologyHistory.Event unfinishedEvent2 = mock(TopologyHistory.Event.class);
      doReturn(new Date(3)).when(unfinishedEvent2).getTime();
      doReturn(EventType.START).when(unfinishedEvent2).getType();

      TopologyHistory.Event finishedEvent1 = mock(TopologyHistory.Event.class);
      doReturn(new Date(2)).when(finishedEvent1).getTime();
      doReturn(EventType.END).when(finishedEvent1).getType();

      TopologyHistory.Event finishedEvent2 = mock(TopologyHistory.Event.class);
      doReturn(new Date(4)).when(finishedEvent2).getTime();
      doReturn(EventType.END).when(finishedEvent2).getType();

      List<TopologyHistory.Event> topologyEvents1 = new ArrayList<>(4);
      topologyEvents1.add(finishedEvent1);
      topologyEvents1.add(finishedEvent2);
      topologyEvents1.add(unfinishedEvent1);
      topologyEvents1.add(unfinishedEvent2);

      List<TopologyHistory.Event> topologyEvents2 = new ArrayList<>(4);
      topologyEvents2.add(unfinishedEvent1);
      topologyEvents2.add(finishedEvent1);
      topologyEvents2.add(finishedEvent2);
      topologyEvents2.add(unfinishedEvent2);

      List<TopologyHistory.Event> topologyEvents3 = new ArrayList<>(4);
      topologyEvents3.add(finishedEvent1);
      topologyEvents3.add(unfinishedEvent1);
      topologyEvents3.add(unfinishedEvent2);
      topologyEvents3.add(finishedEvent2);

      List<TopologyHistory.Event> topologyEvents4 = new ArrayList<>(2);
      topologyEvents4.add(unfinishedEvent1);
      topologyEvents4.add(unfinishedEvent2);

      List<TopologyHistory.Event> topologyEvents5 = new ArrayList<>(4);
      topologyEvents5.add(unfinishedEvent1);
      topologyEvents5.add(finishedEvent1);
      topologyEvents5.add(unfinishedEvent2);
      topologyEvents5.add(finishedEvent2);

      if (checkEvents.contains(HistoryType.TOPOLOGY)) {
         doReturn(topologyEvents1)
            .doReturn(topologyEvents2)
            .doReturn(topologyEvents3)
            .doReturn(topologyEvents4)
            .doReturn(topologyEvents5)
            .when(topologyHistory).getTopologyChangeHistory(anyString());
      }
      if (checkEvents.contains(HistoryType.REHASH)) {
         doReturn(topologyEvents1)
            .doReturn(topologyEvents2)
            .doReturn(topologyEvents3)
            .doReturn(topologyEvents4)
            .doReturn(topologyEvents5)
            .when(topologyHistory).getRehashHistory(anyString());
      }
      if (checkEvents.contains(HistoryType.CACHE_STATUS)) {
         doReturn(topologyEvents1)
            .doReturn(topologyEvents2)
            .doReturn(topologyEvents3)
            .doReturn(topologyEvents4)
            .doReturn(topologyEvents5)
            .when(topologyHistory).getCacheStatusChangeHistory(anyString());
      }

      PowerMockito.mockStatic(TimeService.class);
      PowerMockito.when(TimeService.currentTimeMillis()).thenReturn(10l);

      DistStageAck distStageAck = stage.executeOnSlave();
      assertFalse(distStageAck.isError());

      if (checkEvents.contains(HistoryType.TOPOLOGY)) {
         verify(topologyHistory, times(5)).getTopologyChangeHistory(anyString());
      }
      if (checkEvents.contains(HistoryType.REHASH)) {
         verify(topologyHistory, times(5)).getRehashHistory(anyString());
      }
      if (checkEvents.contains(HistoryType.CACHE_STATUS)) {
         verify(topologyHistory, times(5)).getCacheStatusChangeHistory(anyString());
      }
   }

   public void testExecuteOnSlaveSingleEvents() throws NoSuchFieldException, IllegalAccessException {
      WaitForTopologySettleStage stage = initStage(EnumSet.of(HistoryType.CACHE_STATUS), false);
      setClassProperty(WaitForTopologySettleStage.class, stage, "period", 10l);
      TopologyHistory topologyHistory = mock(TopologyHistory.class);
      setClassProperty(WaitForTopologySettleStage.class, stage, "history", topologyHistory);

      doReturn(true).when(stage).isServiceRunning();

      TopologyHistory.Event singleEvent1 = mock(TopologyHistory.Event.class);
      doReturn(new Date(1)).when(singleEvent1).getTime();
      doReturn(EventType.SINGLE).when(singleEvent1).getType();

      TopologyHistory.Event singleEvent2 = mock(TopologyHistory.Event.class);
      doReturn(new Date(3)).when(singleEvent2).getTime();
      doReturn(EventType.SINGLE).when(singleEvent2).getType();

      List<TopologyHistory.Event> topologyEvents = new ArrayList<>(2);
      topologyEvents.add(singleEvent1);
      topologyEvents.add(singleEvent2);

      doReturn(topologyEvents).when(topologyHistory).getCacheStatusChangeHistory(anyString());

      PowerMockito.mockStatic(TimeService.class);
      PowerMockito.when(TimeService.currentTimeMillis())
         .thenReturn(0l).thenReturn(5l)
         .thenReturn(15l);

      DistStageAck distStageAck = stage.executeOnSlave();
      assertFalse(distStageAck.isError());

      verify(topologyHistory, times(2)).getCacheStatusChangeHistory(anyString());
   }

   private WaitForTopologySettleStage initStage(EnumSet<TopologyHistory.HistoryType> checkEvents,
                                                boolean checkMembership) throws NoSuchFieldException, IllegalAccessException {
      WaitForTopologySettleStage stage = spy(new WaitForTopologySettleStage());
      stage.initOnSlave(mock(SlaveState.class));

      TopologyHistory topologyHistory = mock(TopologyHistory.class);
      Log log = mock(Log.class);
      setClassProperty(WaitForTopologySettleStage.class, stage, "history", topologyHistory);
      setClassProperty(AbstractDistStage.class, stage, "log", log);
      setClassProperty(WaitForTopologySettleStage.class, stage, "checkEvents", checkEvents);
      setClassProperty(WaitForTopologySettleStage.class, stage, "checkMembership", checkMembership);
      return stage;
   }
}

package org.radargun.service;

import org.testng.annotations.Test;

import javax.management.MBeanServerConnection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.radargun.traits.TopologyHistory.Event.EventType;
import static org.radargun.util.ReflectionUtils.setClassProperty;
import static org.testng.Assert.*;

/**
 * @author Matej Cimbora
 */
@Test
public class Infinispan60ServerTopologyHistoryTest {

   public void testCacheStatus() throws NoSuchFieldException, IllegalAccessException {
      InfinispanServerService service = mock(InfinispanServerService.class);
      setClassProperty(InfinispanServerService.class, service, "connection", mock(MBeanServerConnection.class));
      setClassProperty(InfinispanServerService.class, service, "lifecycle", mock(InfinispanServerLifecycle.class));
      Infinispan60ServerTopologyHistory history = spy(new Infinispan60ServerTopologyHistory(service));

      Map<String, Object> rehashJmxQueryResult = new HashMap<>(2);
      rehashJmxQueryResult.put("cache1", false);
      rehashJmxQueryResult.put("cache2", true);
      Map<String, Object> topologyJmxQueryResult = new HashMap<>(2);
      topologyJmxQueryResult.put("cache1", "pending_view_1");
      topologyJmxQueryResult.put("cache2", "null");
      Map<String, Object> cacheStatusJmxQueryResult = new HashMap<>(2);
      cacheStatusJmxQueryResult.put("cache1", "AVAILABLE");
      cacheStatusJmxQueryResult.put("cache2", "DEGRADED_MODE");
      doReturn(rehashJmxQueryResult).doReturn(topologyJmxQueryResult).doReturn(cacheStatusJmxQueryResult).when(history).retrieveJMXAttributeValues(any(MBeanServerConnection.class), anyString(), anyString());

      Map<String, Infinispan60ServerTopologyHistory.CacheStatus> statusMap = history.cacheStatus();
      assertEquals(statusMap.size(), 3);
      Infinispan60ServerTopologyHistory.CacheStatus cacheStatus1 = statusMap.get("cache1");
      assertEquals(cacheStatus1.rehashInProgress, false);
      assertEquals(cacheStatus1.topologyChangeInProgress, true);
      assertEquals(cacheStatus1.prevCacheAvailability, Infinispan60ServerTopologyHistory.CacheAvailability.AVAILABLE);
      Infinispan60ServerTopologyHistory.CacheStatus cacheStatus2 = statusMap.get("cache2");
      assertEquals(cacheStatus2.rehashInProgress, true);
      assertEquals(cacheStatus2.topologyChangeInProgress, false);
      assertEquals(cacheStatus2.prevCacheAvailability, Infinispan60ServerTopologyHistory.CacheAvailability.DEGRADED_MODE);
      Infinispan60ServerTopologyHistory.CacheStatus cacheStatus3 = statusMap.get(Infinispan60ServerTopologyHistory.ALL_CACHES);
      assertEquals(cacheStatus3.rehashInProgress, true);
      assertEquals(cacheStatus3.topologyChangeInProgress, true);
      assertEquals(cacheStatus3.prevCacheAvailability, null);


      Map<String, Infinispan60ServerTopologyHistory.CacheStatus> cacheChangesOngoing = history.cacheChangesOngoing;
      assertEquals(cacheChangesOngoing.size(), 0);

      doReturn(statusMap).when(history).cacheStatus();
      history.processCacheStatus();
      assertEquals(cacheChangesOngoing.size(), 3);

      // check rehash events
      Map<String, List<AbstractTopologyHistory.Event>> events = history.hashChanges;
      // cache2, ALL_CACHES
      assertEquals(events.size(), 2);
      List<AbstractTopologyHistory.Event> eventList = events.get("cache2");
      assertEquals(eventList.size(), 1);
      assertEquals(eventList.get(0).getType(), EventType.START);
      List<AbstractTopologyHistory.Event> eventList2 = events.get(Infinispan60ServerTopologyHistory.ALL_CACHES);
      assertEquals(eventList2.size(), 1);
      assertEquals(eventList2.get(0).getType(), EventType.START);

      // check topology events
      events = history.topologyChanges;
      // cache1, ALL_CACHES
      assertEquals(events.size(), 2);
      eventList = events.get("cache1");
      assertEquals(eventList.size(), 1);
      assertEquals(eventList.get(0).getType(), EventType.START);
      eventList2 = events.get(Infinispan60ServerTopologyHistory.ALL_CACHES);
      assertEquals(eventList2.size(), 1);
      assertEquals(eventList2.get(0).getType(), EventType.START);

      // check cache status changes
      events = history.cacheStatusChanges;
      // no changes
      assertEquals(events.size(), 0);

      // second round of event processing
      rehashJmxQueryResult = new HashMap<>(2);
      rehashJmxQueryResult.put("cache1", true);
      rehashJmxQueryResult.put("cache2", false);
      topologyJmxQueryResult = new HashMap<>(2);
      topologyJmxQueryResult.put("cache1", "null");
      topologyJmxQueryResult.put("cache2", "pending_view_2");
      cacheStatusJmxQueryResult = new HashMap<>(2);
      cacheStatusJmxQueryResult.put("cache1", "DEGRADED_MODE");
      cacheStatusJmxQueryResult.put("cache2", "AVAILABLE");
      doReturn(rehashJmxQueryResult).doReturn(topologyJmxQueryResult).doReturn(cacheStatusJmxQueryResult).when(history).retrieveJMXAttributeValues(any(MBeanServerConnection.class), anyString(), anyString());

      when(history.cacheStatus()).thenCallRealMethod();
      statusMap = history.cacheStatus();

      doReturn(statusMap).when(history).cacheStatus();
      history.processCacheStatus();
      assertEquals(cacheChangesOngoing.size(), 3);

      // check rehash events
      events = history.hashChanges;
      // cache1, cache2, ALL_CACHES
      assertEquals(events.size(), 3);
      eventList = events.get("cache1");
      assertEquals(eventList.size(), 1);
      assertEquals(eventList.get(0).getType(), EventType.START);
      eventList = events.get("cache2");
      assertEquals(eventList.size(), 2);
      assertEquals(eventList.get(0).getType(), EventType.START);
      assertEquals(eventList.get(1).getType(), EventType.END);
      eventList2 = events.get(Infinispan60ServerTopologyHistory.ALL_CACHES);
      assertEquals(eventList2.size(), 1);
      assertEquals(eventList2.get(0).getType(), EventType.START);

      // check topology events
      events = history.topologyChanges;
      // cache1, cache2, ALL_CACHES
      assertEquals(events.size(), 3);
      eventList = events.get("cache1");
      assertEquals(eventList.size(), 2);
      assertEquals(eventList.get(0).getType(), EventType.START);
      assertEquals(eventList.get(1).getType(), EventType.END);
      eventList = events.get("cache2");
      assertEquals(eventList.size(), 1);
      assertEquals(eventList.get(0).getType(), EventType.START);
      eventList2 = events.get(Infinispan60ServerTopologyHistory.ALL_CACHES);
      assertEquals(eventList2.size(), 1);
      assertEquals(eventList.get(0).getType(), EventType.START);

      // check cache status changes
      events = history.cacheStatusChanges;
      // cache1, cache2, ALL_CACHES
      assertEquals(events.size(), 3);
      eventList = events.get("cache1");
      assertEquals(eventList.size(), 1);
      assertEquals(eventList.get(0).getType(), EventType.SINGLE);
      eventList = events.get("cache2");
      assertEquals(eventList.size(), 1);
      assertEquals(eventList.get(0).getType(), EventType.SINGLE);
      eventList2 = events.get(Infinispan60ServerTopologyHistory.ALL_CACHES);
      assertEquals(eventList2.size(), 1);
      assertEquals(eventList2.get(0).getType(), EventType.SINGLE);
   }
}

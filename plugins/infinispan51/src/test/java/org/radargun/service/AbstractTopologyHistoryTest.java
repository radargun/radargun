package org.radargun.service;

import org.radargun.traits.TopologyHistory;
import org.radargun.utils.Utils;
import org.testng.annotations.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.radargun.traits.TopologyHistory.Event.EventType;
import static org.testng.Assert.*;

/**
 * @author Matej Cimbora
 */
@Test
public class AbstractTopologyHistoryTest {

   public void testEventCreationFinished() {
      AbstractTopologyHistory.Event event = new AbstractTopologyHistory.Event(new Date(), EventType.START, 1, 2);
      assertNotNull(event.getTime());
      assertEquals(event.getType(), EventType.START);
      assertEquals(event.getMembersAtStart(), 1);
      assertEquals(event.getMembersAtEnd(), 2);
   }

   public void testEventCreationNotFinished() {
      AbstractTopologyHistory.Event event = new AbstractTopologyHistory.Event(new Date(), EventType.END, 1, 2);
      assertNotNull(event.getTime());
      assertEquals(event.getType(), EventType.END);
      assertEquals(event.getMembersAtStart(), 1);
      assertEquals(event.getMembersAtEnd(), 2);
   }

   public void testEventCreationSingle() {
      AbstractTopologyHistory.Event event = new AbstractTopologyHistory.Event(new Date(), EventType.SINGLE, 1, 2);
      assertNotNull(event.getTime());
      assertEquals(event.getType(), EventType.SINGLE);
      assertEquals(event.getMembersAtStart(), 1);
      assertEquals(event.getMembersAtEnd(), 2);
   }

   public void testAddEvent() {
      AbstractTopologyHistory history = mock(AbstractTopologyHistory.class, CALLS_REAL_METHODS);
      Map<String, List<AbstractTopologyHistory.Event>> eventMap = new HashMap<>(5);

      history.addEvent(eventMap, "test", EventType.START, 1, 2);
      List<AbstractTopologyHistory.Event> eventList = eventMap.get("test");
      assertEquals(eventList.size(), 1);
      AbstractTopologyHistory.Event event = eventList.get(0);
      assertNotNull(event.getTime());

      Utils.sleep(1);
      history.addEvent(eventMap, "test", EventType.END, 1, 2);
      assertEquals(eventList.size(), 2);
      event = eventList.get(1);
      assertNotNull(event.getTime());
      assertEquals(event.getType(), EventType.END);
      assertTrue(eventList.get(0).getTime().compareTo(eventList.get(1).getTime()) < 0);

      Utils.sleep(1);
      history.addEvent(eventMap, "test", EventType.END, 1, 2);
      assertEquals(eventList.size(), 3);
      event = eventList.get(2);
      assertEquals(event.getType(), EventType.END);
      assertTrue(eventList.get(1).getTime().compareTo(eventList.get(2).getTime()) < 0);

      Utils.sleep(1);
      history.addEvent(eventMap, "test", EventType.SINGLE, 1, 2);
      assertEquals(eventList.size(), 4);
      event = eventList.get(3);
      assertEquals(event.getType(), EventType.SINGLE);
      assertTrue(eventList.get(2).getTime().compareTo(eventList.get(3).getTime()) < 0);

      Utils.sleep(1);
      history.addEvent(eventMap, "test", EventType.END, 1, 2);
      assertEquals(eventList.size(), 5);
      event = eventList.get(4);
      assertEquals(event.getType(), EventType.END);
      assertTrue(eventList.get(3).getTime().compareTo(eventList.get(4).getTime()) < 0);

      Utils.sleep(1);
      history.addEvent(eventMap, "test", EventType.START, 1, 2);
      assertEquals(eventList.size(), 6);
      event = eventList.get(5);
      assertEquals(event.getType(), EventType.START);
      assertTrue(eventList.get(4).getTime().compareTo(eventList.get(5).getTime()) < 0);
   }
}

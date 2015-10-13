package org.radargun.service;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.EntryListener;
import com.hazelcast.core.IMap;
import com.hazelcast.core.MapEvent;
import org.radargun.traits.ContinuousQuery;
import org.radargun.traits.Query;

/**
 * @author vjuranek
 */
public class HazelcastContinuousQuery implements ContinuousQuery {

    protected final Hazelcast3Service service;
    private String hazelcastCQListenerId;

    public HazelcastContinuousQuery(Hazelcast3Service service) {
        this.service = service;
    }

    @Override
    public void createContinuousQuery(String mapName, Query query, ContinuousQueryListener cqListener) {
        hazelcastCQListenerId = getMap(mapName).addEntryListener(new HazelcastContinuousQueryListener(cqListener), ((HazelcastQuery) query).getPredicate(), true);
    }

    @Override
    public void removeContinuousQuery(String mapName, ContinuousQueryListener cqListener) {
        if (hazelcastCQListenerId != null) {
            getMap(mapName).removeEntryListener(hazelcastCQListenerId);
        }
    }

    protected IMap<Object, Object> getMap(String mapName) {
        return service.getMap(mapName);
    }

    public static class HazelcastContinuousQueryListener implements EntryListener {

        private final ContinuousQueryListener cqListener;

        public HazelcastContinuousQueryListener(ContinuousQueryListener cqListener) {
            this.cqListener = cqListener;
        }

        @Override
        public void entryAdded(EntryEvent entryEvent) {
            cqListener.onEntryJoined(entryEvent.getKey(), entryEvent.getValue());
        }

        @Override
        public void entryRemoved(EntryEvent entryEvent) {
            cqListener.onEntryLeft(entryEvent.getKey());
        }

        @Override
        public void entryUpdated(EntryEvent entryEvent) {
            //TODO check, if this is correct
            cqListener.onEntryJoined(entryEvent.getKey(), entryEvent.getValue());
        }

        @Override
        public void entryEvicted(EntryEvent entryEvent) {
            cqListener.onEntryLeft(entryEvent.getKey());
        }

        @Override
        public void mapCleared(MapEvent mapEvent) {
        }

        @Override
        public void mapEvicted(MapEvent mapEvent) {
        }
    }
}

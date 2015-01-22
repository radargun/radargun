package org.radargun.service;

import java.util.Collections;
import java.util.List;

import com.hazelcast.core.IMap;
import com.hazelcast.core.MembershipListener;
import org.radargun.Service;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;
import org.radargun.traits.ProvidesTrait;
import org.radargun.traits.Transactional;
import org.radargun.utils.ReflexiveConverters;

/**
 * An implementation of CacheWrapper that uses Hazelcast instance as an underlying implementation.
 * @author Maido Kaara
 */
@Service(doc = "Hazelcast")
public class Hazelcast3Service extends HazelcastService {
   @Property(doc = "Indices that should be build.", complexConverter = IndexConverter.class)
   protected List<Index> indices = Collections.EMPTY_LIST;

   @ProvidesTrait
   @Override
   public Transactional createTransactional() {
      return new Hazelcast3Transactional(this);
   }

   @ProvidesTrait
   @Override
   public HazelcastOperations createOperations() {
      return new Hazelcast3Operations(this);
   }

   @ProvidesTrait
   public HazelcastQueryable createQueryable() {
      return new HazelcastQueryable(this);
   }

   @Override
   protected void addMembershipListener(MembershipListener listener) {
      // Cluster interface changed 2 -> 3, binary compatibility was broken
      hazelcastInstance.getCluster().addMembershipListener(listener);
   }

   @Override
   protected <K, V> IMap<K, V> getMap(String mapName) {
      IMap<K, V> map = super.getMap(mapName);
      for (Index index : indices) {
         synchronized (index) {
            if (index.added) {
               continue;
            }
            if ((index.mapName == null && map.getName().equals(this.mapName))
                  || (index.mapName != null && map.getName().equals(index.mapName))) {
               map.addIndex(index.path, index.ordered);
               index.added = true;
            }
         }
      }
      return map;
   }

   @DefinitionElement(name = "index", doc = "Index definition.")
   protected static class Index {
      @Property(doc = "Map on which the index should be built. Default is the default map.")
      protected String mapName;

      @Property(doc = "Should be the index ordered? Default is true")
      protected boolean ordered = true;

      @Property(doc = "Path in the indexed object.", optional = false)
      protected String path;

      // whether we have registered this index
      private boolean added = false;
   }

   protected static class IndexConverter extends ReflexiveConverters.ListConverter {
      public IndexConverter() {
         super(new Class[] { Index.class });
      }
   }
}

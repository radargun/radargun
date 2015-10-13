package org.radargun.service;

import com.hazelcast.core.IMap;
import com.hazelcast.query.Predicate;
import org.radargun.traits.Query;

public class HazelcastQuery implements Query {
   private HazelcastQueryable hazelcastQueryable;
   private final IMap map;
   private final Predicate predicate;
   private final int offset;
   private final String[] projection;

   public HazelcastQuery(HazelcastQueryable hazelcastQueryable, IMap map, Predicate predicate, int offset, String[] projection) {
      this.hazelcastQueryable = hazelcastQueryable;
      this.map = map;
      this.predicate = predicate;
      this.offset = offset;
      this.projection = projection;
   }

   @Override
   public QueryResult execute() {
      if (predicate == null) return new HazelcastQueryable.HazelcastQueryResult(map.values(), offset, projection);
      else return new HazelcastQueryable.HazelcastQueryResult(map.values(predicate), offset, projection);
   }

   public Predicate getPredicate() {
      return predicate;
   }
}

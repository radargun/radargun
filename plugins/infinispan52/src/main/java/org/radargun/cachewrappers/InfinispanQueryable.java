/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.radargun.cachewrappers;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.Store;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.hibernate.search.query.dsl.TermTermination;
import org.infinispan.Cache;
import org.infinispan.query.CacheQuery;
import org.infinispan.query.Search;
import org.infinispan.query.SearchManager;
import org.radargun.features.Queryable;

/**
 * Wrapper which will be able to run queries on Infinispan caches.
 *
 * @author Anna Manukyan
 */
public class InfinispanQueryable implements Queryable, Serializable {

   protected Infinispan52Wrapper wrapper;

   public InfinispanQueryable(Infinispan52Wrapper wrapper) {
      this.wrapper = wrapper;
   }

   @Override
   public QueryResultImpl executeQuery(Map<String, Object> queryParameters) {
      Cache cache = wrapper.getCache(null);

      SearchManager searchManager = Search.getSearchManager(cache);
      QueryBuilder queryBuilder = searchManager.buildQueryBuilderForClass(QueryableData.class).get();
      TermTermination termTermination;
      Boolean isWildcardQuery = (Boolean) queryParameters.get(IS_WILDCARD);
      String onField = (String) queryParameters.get(QUERYABLE_FIELD);
      String matching = (String) queryParameters.get(MATCH_STRING);

      if (isWildcardQuery) {
         termTermination = queryBuilder.keyword().wildcard().onField(onField).matching(matching);
      } else {
         termTermination = queryBuilder.keyword().onField(onField).matching(matching);
      }

      CacheQuery cacheQuery = searchManager.getQuery(termTermination.createQuery());

      return new QueryResultImpl(cacheQuery);
   }

   Object wrapForQuery(Object value) {
      return new QueryableData((String) value);
   }

   @Indexed(index = "query")
   public class QueryableData implements Serializable {

      @Field(store = Store.YES)
      private String description;

      public QueryableData(String description) {
         this.description = description;
      }

      public String getDescription() {
         return description;
      }

      public String toString() {
         return description;
      }
   }

   public class QueryResultImpl implements QueryResult {
      private CacheQuery cacheQuery;

      public QueryResultImpl(final CacheQuery cacheQuery) {
         this.cacheQuery = cacheQuery;
      }

      public int size() {
         return cacheQuery.getResultSize();
      }

      public List list() {
         return cacheQuery.list();
      }
   }
}

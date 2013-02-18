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

import java.util.Map;

import org.infinispan.Cache;
import org.infinispan.distexec.mapreduce.Collator;
import org.infinispan.distexec.mapreduce.MapReduceTask;
import org.infinispan.distexec.mapreduce.Mapper;
import org.infinispan.distexec.mapreduce.Reducer;
import org.radargun.features.MapReduceCapable;
import org.radargun.utils.ClassLoadHelper;

public class InfinispanMapReduceWrapper extends InfinispanKillableWrapper implements MapReduceCapable {

   @SuppressWarnings({ "unchecked", "rawtypes" })
   @Override
   public Object executeMapReduceTask(ClassLoadHelper classLoadHelper, String mapperFqn, String reducerFqn,
         String collatorFqn) {
      Cache cache = cacheManager.getCache(getCacheName());
      MapReduceTask t = new MapReduceTask(cache);

      Mapper<?, ?, ?, ?> mapper = null;
      Reducer<?, ?> reducer = null;
      Collator<?, ?, ?> collator = null;

      Object result = null;

      try {
         mapper = (Mapper<?, ?, ?, ?>) classLoadHelper.createInstance(mapperFqn);
         t = t.mappedWith(mapper);
      } catch (Exception e) {
         log.fatal("Could not instantiate Mapper class: " + mapperFqn, e);
      }

      try {
         reducer = (Reducer<?, ?>) classLoadHelper.createInstance(reducerFqn);
         t = t.reducedWith(reducer);
      } catch (Exception e) {
         log.fatal("Could not instantiate Reducer class: " + reducerFqn, e);
      }

      try {
         collator = (Collator<?, ?, ?>) classLoadHelper.createInstance(collatorFqn);
      } catch (Exception e) {
         log.fatal("Could not instantiate Collator class: " + collatorFqn, e);
      }

      if (mapper != null && reducer != null && collator != null) {
         result = t.execute(collator);
      }
      return result;
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   @Override
   public Map<?, ?> executeMapReduceTask(ClassLoadHelper classLoadHelper, String mapperFqn, String reducerFqn) {
      Cache cache = cacheManager.getCache(getCacheName());
      MapReduceTask t = new MapReduceTask(cache);

      Mapper<?, ?, ?, ?> mapper = null;
      Reducer<?, ?> reducer = null;

      Map<?, ?> result = null;

      try {
         mapper = (Mapper<?, ?, ?, ?>) classLoadHelper.createInstance(mapperFqn);
         t = t.mappedWith(mapper);
      } catch (Exception e) {
         log.fatal("Could not instantiate Mapper class: " + mapperFqn, e);
      }

      try {
         reducer = (Reducer<?, ?>) classLoadHelper.createInstance(reducerFqn);
         t = t.reducedWith(reducer);
      } catch (Exception e) {
         log.fatal("Could not instantiate Reducer class: " + reducerFqn, e);
      }

      if (mapper != null && reducer != null) {
         result = t.execute();
      }

      return result;
   }

}

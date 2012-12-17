package org.radargun.cachewrappers;

import java.util.Map;

import org.infinispan.Cache;
import org.infinispan.distexec.mapreduce.MapReduceTask;
import org.infinispan.distexec.mapreduce.Mapper;
import org.infinispan.distexec.mapreduce.Reducer;
import org.radargun.features.MapReduceCapable;
import org.radargun.utils.ClassLoadHelper;

public class InfinispanMapReduceWrapper extends InfinispanKillableWrapper implements MapReduceCapable {
	
	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Integer> executeMapReduceTask(ClassLoadHelper classLoadHelper,
			String mapperFqn,
			String reducerFqn) {
		Cache<String, String> cache = cacheManager.getCache(getCacheName());
		MapReduceTask<String, String, String, Integer> t =
		         new MapReduceTask<String, String, String, Integer>(cache);
		
		Mapper<String, String, String, Integer> mapper;
		try {
			mapper = (Mapper<String, String, String, Integer>) classLoadHelper.createInstance(mapperFqn);
			t.mappedWith(mapper);
			Reducer<String, Integer> reducer;
			try {
				reducer = (Reducer<String, Integer>) classLoadHelper.createInstance(reducerFqn);
				t.reducedWith(reducer);
				return t.execute();
			} catch (Exception e) {
				log.fatal("Could not instantiate Reducer class: " + reducerFqn, e);
			}			
		} catch (Exception e) {
			log.fatal("Could not instantiate Mapper class: " + mapperFqn, e);
		}
		return null;
	}
}

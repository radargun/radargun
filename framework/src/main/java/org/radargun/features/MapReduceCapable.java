package org.radargun.features;

import java.util.Map;

import org.radargun.CacheWrapper;
import org.radargun.utils.ClassLoadHelper;

public interface MapReduceCapable extends CacheWrapper {
	
	public Map<String, Integer> executeMapReduceTask(ClassLoadHelper classLoadHelper, 
			String mapperFqn, String reducerFqn);

}

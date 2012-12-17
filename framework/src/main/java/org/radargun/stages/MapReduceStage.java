package org.radargun.stages;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.features.MapReduceCapable;

/**
 * Executes a Map/Reduce Task against the cache.
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Stage(doc = "Benchmark which executes a Map/Reduce Task against the cache.")
public class MapReduceStage extends AbstractDistStage {

	private CacheWrapper cacheWrapper;

	@Property(optional = false, doc = "Fully qualified class name of the " +
			  "org.infinispan.distexec.mapreduce.Mapper implementation to execute.")
	private String mapperFqn;

	@Property(optional = false, doc = "Fully qualified class name of the " +
			  "org.infinispan.distexec.mapreduce.Reducer implementation to execute.")
	private String reducerFqn;

	@Override
	public DistStageAck executeOnSlave() {
		DefaultDistStageAck result = newDefaultStageAck();
		cacheWrapper = slaveState.getCacheWrapper();
		if (cacheWrapper == null) {
			result.setErrorMessage("Not running test on this slave as the wrapper hasn't been configured.");
		} else {
			if (slaveState.getCacheWrapper() instanceof MapReduceCapable) {
				if (mapperFqn == null) {
					result.setError(true);
					result.setErrorMessage("No mapper class specified.");
					return result;
				}

				if (reducerFqn == null) {
					result.setError(true);
					result.setErrorMessage("No reducer class specified.");
					return result;
				}
				
				result.setPayload(((MapReduceCapable) cacheWrapper).executeMapReduceTask(classLoadHelper, mapperFqn, reducerFqn));
				
			} else {
				result.setErrorMessage("Map/Reduce tasks are not supported by this cache");
			}
		}
		
		return result;
	}

	public String getMapperFqn() {
		return mapperFqn;
	}

	public void setMapperFqn(String mapperFqn) {
		this.mapperFqn = mapperFqn;
	}

	public String getReducerFqn() {
		return reducerFqn;
	}

	public void setReducerFqn(String reducerFqn) {
		this.reducerFqn = reducerFqn;
	}
	
}

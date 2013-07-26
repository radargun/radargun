package org.radargun.features;

import java.util.concurrent.Future;

import org.radargun.CacheWrapper;

public interface AsyncOperationsCapable extends CacheWrapper {
    Future<Object> putAsync(String bucket, Object key, Object value) throws Exception;

    Future<Object> getAsync(String bucket, Object key) throws Exception;

    Future<Object> removeAsync(String bucket, Object key) throws Exception;

}

package org.cachebench.cachewrappers;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.NamedCache;
import com.tangosol.util.TransactionMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;

import javax.transaction.Transaction;
import java.util.List;
import java.util.Map;

/**
 * Pass in a -Dtangosol.coherence.localhost=IP_ADDRESS
 *
 * @author <a href="mailto:manik@jboss.org">Manik Surtani</a>
 * @since 2.0.0
 */
public class Coherence3Wrapper implements CacheWrapper {

    private TransactionMap cache;
    private NamedCache nc;
    private Log log = LogFactory.getLog(Coherence3Wrapper.class);

    @Override
    public void setUp(String configuration) throws Exception {
        if (configuration.indexOf("repl") == 0) {
            nc = CacheFactory.getCache("repl-CacheBenchmarkFramework");
        } else if (configuration.indexOf("dist") == 0) {
            nc = CacheFactory.getCache("dist-CacheBenchmarkFramework");
        } else if (configuration.indexOf("local") == 0) {
            nc = CacheFactory.getCache("local-CacheBenchmarkFramework");
        } else if (configuration.indexOf("opt") == 0) {
            nc = CacheFactory.getCache("opt-CacheBenchmarkFramework");
        } else if (configuration.indexOf("near") == 0) {
            nc = CacheFactory.getCache("near-CacheBenchmarkFramework");
        } else
            throw new RuntimeException("Invalid configuration ('" + configuration + "'). Configuration name should start with: 'dist', 'repl', 'local', 'opt' or 'near'");

        cache = CacheFactory.getLocalTransaction(nc);
        log.info("Starting Coherence cache " + nc.getCacheName());
    }

    public void tearDown() throws Exception {
        if (cache != null) nc.release();
    }

    @Override
    public void put(String bucket, Object key, Object value) throws Exception {
        cache.lock(key);
        try {
            cache.put(key, value);
        }
        finally {
            cache.unlock(key);
        }
    }

    @Override
    public Object get(String bucket, Object key) throws Exception {
        try {
            return cache.get(key);
        }
        finally {
            cache.unlock(key);
        }
    }

    public void empty() throws Exception {
        cache.clear();
    }

    public int getNumMembers() {
        return nc.getCacheService().getCluster().getMemberSet().size();
    }

    public String getInfo() {
        return nc.getCacheName();
    }

    @Override
    public Object getReplicatedData(String bucket, String key) throws Exception {
         return get(bucket, key);
    }

    public Transaction startTransaction() {
        throw new UnsupportedOperationException("Does not support JTA!");
    }

    public void endTransaction(boolean successful) {
        throw new UnsupportedOperationException("Does not support JTA!");
    }
}

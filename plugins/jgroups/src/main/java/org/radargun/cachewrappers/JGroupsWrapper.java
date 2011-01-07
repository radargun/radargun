package org.radargun.cachewrappers;

import org.jgroups.JChannel;
import org.jgroups.Version;
import org.jgroups.View;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.jgroups.util.Util;
import org.radargun.CacheWrapper;

import javax.transaction.TransactionManager;

public class JGroupsWrapper implements CacheWrapper {
    private static Log log=LogFactory.getLog(JGroupsWrapper.class);
    JChannel ch;
    TransactionManager tm;
    boolean started=false;
    String config;

    public void setUp(String config, boolean isLocal, int nodeIndex) throws Exception {
        this.config=config;
        if(!started) {
            ch=new JChannel(config);
            ch.connect("radargun-cluster");
            started=true;
        }
        log.info("Loading JGroups form: " + org.jgroups.Version.class.getProtectionDomain().getCodeSource().getLocation());
        log.info("JGroups version: " + org.jgroups.Version.printDescription());
    }

    public void tearDown() throws Exception {
        Util.close(ch);
        started=false;
    }

    public void put(String bucket, Object key, Object value) throws Exception {
        throw new UnsupportedOperationException();
    }

    public Object get(String bucket, Object key) throws Exception {
        throw new UnsupportedOperationException();
    }

    public void empty() throws Exception {
        ; // no-op
    }

    public int getNumMembers() {
        View view=ch.getView();
        return view != null? view.size() : 0;
    }

    public String getInfo() {
        StringBuilder sb=new StringBuilder();
        sb.append("view=" + ch.getViewAsString()).append(" [version=").append(Version.printVersion()).append("]");
        return sb.toString();
    }

    public Object getReplicatedData(String bucket, String key) throws Exception {
        return get(bucket, key);
    }

    public Object startTransaction() {
        if(tm == null) return null;
        try {
            tm.begin();
            return tm.getTransaction();
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void endTransaction(boolean successful) {
        if(tm == null) return;
        try {
            if(successful)
                tm.commit();
            else
                tm.rollback();
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
}

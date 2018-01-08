package org.radargun.service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Clustered;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;
import org.radargun.traits.Transactional;

/**
 *
 * An implementation of CacheWrapper that uses Hazelcast instance as an underlying implementation.
 * @author Martin Gencur
 *
 */
@Service(doc = HazelcastService.SERVICE_DESCRIPTION)
public class HazelcastService implements Lifecycle, Clustered {

   protected static final String SERVICE_DESCRIPTION = "Hazelcast";

   protected final Log log = LogFactory.getLog(getClass());
   private final boolean trace = log.isTraceEnabled();

   protected HazelcastInstance hazelcastInstance;
   protected List<Membership> membershipHistory = new ArrayList<>();

   @Property(name = Service.FILE, doc = "Configuration file.")
   private String config;

   @Property(name = "cache", doc = "Name of the map ~ cache", deprecatedName = "map")
   protected String mapName = "default";

   @Property(name = "useTransactions", doc = "Whether the service should use transactions. Default is false.")
   protected boolean useTransactions = false;

   @ProvidesTrait
   public HazelcastService getSelf() {
      return this;
   }

   @ProvidesTrait
   public Transactional createTransactional() {
      return new HazelcastTransactional(this);
   }

   @ProvidesTrait
   public HazelcastCacheInfo createCacheInfo() {
      return new HazelcastCacheInfo(this);
   }

   @ProvidesTrait
   public HazelcastOperations createOperations() {
      return new HazelcastOperations(this);
   }

   @Override
   public void start() {
      log.info("Creating cache with the following configuration: " + config);
      try (InputStream configStream = getAsInputStreamFromClassLoader(config)) {
         Config cfg = new XmlConfigBuilder(configStream).build();
         hazelcastInstance = Hazelcast.newHazelcastInstance(cfg);
         MembershipListener listener = new MembershipListener() {
            @Override
            public void memberAdded(MembershipEvent membershipEvent) {
               updateMembers(membershipEvent.getMembers());
            }

            @Override
            public void memberRemoved(MembershipEvent membershipEvent) {
               updateMembers(membershipEvent.getMembers());
            }
         };
         synchronized (this) {
            addMembershipListener(listener);
            updateMembers(hazelcastInstance.getCluster().getMembers());
         }
         log.info("Hazelcast configuration:" + hazelcastInstance.getConfig().toString());
      } catch (IOException e) {
         log.error("Failed to get configuration input stream", e);
      }
   }

   protected void addMembershipListener(MembershipListener listener) {
      hazelcastInstance.getCluster().addMembershipListener(listener);
   }

   @Override
   public void stop() {
      hazelcastInstance.getLifecycleService().shutdown();
      updateMembers(Collections.EMPTY_SET);
      hazelcastInstance = null;
   }

   @Override
   public boolean isRunning() {
      return hazelcastInstance != null && hazelcastInstance.getLifecycleService().isRunning();
   }

   @Override
   public boolean isCoordinator() {
      return false;
   }

   @Override
   public synchronized Collection<Member> getMembers() {
      if (membershipHistory.isEmpty()) return null;
      return membershipHistory.get(membershipHistory.size() - 1).members;
   }

   @Override
   public synchronized List<Membership> getMembershipHistory() {
      return new ArrayList<>(membershipHistory);
   }

   protected synchronized void updateMembers(Set<com.hazelcast.core.Member> members) {
      ArrayList<Member> mbrs = new ArrayList<>(members.size());
      for (com.hazelcast.core.Member m : members) {
         mbrs.add(new Member(m.getInetSocketAddress().getHostName() + "(" + m.getUuid() + ")", m.localMember(), false));
      }
      membershipHistory.add(Membership.create(mbrs));
   }

   private InputStream getAsInputStreamFromClassLoader(String filename) {
      InputStream is = null;
      ClassLoader cl = getClass().getClassLoader();
      if (cl != null) {
         is = cl.getResourceAsStream(filename);
      }
      if (is == null) {
         try {
            return new FileInputStream(FileSystems.getDefault().getPath(filename).toFile());
         } catch (FileNotFoundException e) {
            is = null;
         }
      }
      return is;
   }

   protected <K, V> IMap<K, V> getMap(String mapName) {
      if (mapName == null) {
         mapName = this.mapName;
      }
      return hazelcastInstance.getMap(mapName);
   }
}

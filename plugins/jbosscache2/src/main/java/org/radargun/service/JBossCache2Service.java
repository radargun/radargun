package org.radargun.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.jboss.cache.Cache;
import org.jboss.cache.DefaultCacheFactory;
import org.jgroups.Address;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Clustered;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;
import org.radargun.traits.Transactional;

/**
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Service(doc = "JBossCache 2.x")
public class JBossCache2Service implements Lifecycle, Clustered {
   private Log log = LogFactory.getLog(JBossCache2Service.class);
   private HashMap<String, Cache> caches = new HashMap<String, Cache>();

   @Property(name = "file", doc = "Configuration file.")
   private String config;

   @ProvidesTrait
   public JBossCache2Service getSelf() {
      return this;
   }

   @ProvidesTrait
   public JBossCache2Operations createOperations() {
      return new JBossCache2Operations(this);
   }

   @ProvidesTrait
   public Transactional createTransactional() {
      return new JBossCacheTransactional();
   }

   public Cache getCache(String cacheName) {
      Cache cache = caches.get(cacheName);
      if (cache == null) {
         cache = createCache();
         caches.put(cacheName, cache);
      }
      return cache;
   }

   protected Cache createCache() {
      Cache cache;
      log.info("Creating cache with the following configuration: " + config);
      cache = new DefaultCacheFactory().createCache(config);
      log.info("Running cache with following config: " + cache.getConfiguration());
      return cache;
   }

   @Override
   public synchronized void start() {
      log.info("Running follwing JBossCacheVersion: " + org.jboss.cache.Version.version);
      log.info("Running follwing JBossCacheCodeName: " + org.jboss.cache.Version.codename);
      getCache(null);
   }

   @Override
   public synchronized void stop() {
      for (Cache cache : caches.values()) {
         cache.stop();
      }
      caches.clear();
   }

   @Override
   public boolean isRunning() {
      return caches.isEmpty();
   }

   @Override
   public boolean isCoordinator() {
      Cache defaultCache = getCache(null);
      List<Address> members = defaultCache.getMembers();
      return members != null && !members.isEmpty() && members.get(0).equals(defaultCache.getLocalAddress());
   }

   @Override
   public Collection<Member> getMembers() {
      Cache defaultCache = getCache(null);
      ArrayList<Member> members = new ArrayList<>();
      boolean coord = true;
      List<Address> addresses = defaultCache.getMembers();
      for (Address address : addresses) {
         members.add(new Member(address.toString(), defaultCache.getLocalAddress().equals(address), coord));
         coord = false;
      }
      return Collections.unmodifiableCollection(members);
   }

   @Override
   public List<Membership> getMembershipHistory() {
      return Collections.EMPTY_LIST; // TODO
   }
}

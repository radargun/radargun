package org.radargun.service;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Status;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Clustered;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;


/**
 * An implementation of SerializableCacheWrapper that uses EHCache as an underlying implementation.
 *
 * Pass in a -Dbind.address=IP_ADDRESS ehcache propery files allows referencing system properties through syntax
 * ${bind.address}.
 *
 * @author Manik Surtani &lt;msurtani@gmail.com&gt;
 */
@Service(doc = "EHCache")
public class EHCacheService implements Lifecycle, Clustered {
   private final static Log log = LogFactory.getLog(EHCacheService.class);
   protected static final String RMI_SCHEME = "RMI";

   protected CacheManager manager;
   //private Ehcache cache;

   @Property(name = "file", doc = "Configuration file.", deprecatedName = "config")
   private String configFile;
   @Property(name = "cache", doc = "Name of the default cache. Default is 'testCache'.")
   protected String cacheName = "testCache";

   @ProvidesTrait
   public EHCacheService getSelf() {
      return this;
   }

   @ProvidesTrait
   public EHCacheOperations createOperations() {
      return new EHCacheOperations(this);
   }

   @ProvidesTrait
   public EHCacheInfo createInfo() {
      return new EHCacheInfo(this);
   }

   @Override
   public synchronized void start()  {
      if (log.isTraceEnabled()) log.trace("Entering EHCacheService.setUp()");
      log.debug("Initializing the cache with " + configFile);
      URL url = getClass().getClassLoader().getResource(configFile);
      manager = new CacheManager(url);
      log.info("Caches available:");

      for (String s : manager.getCacheNames()) log.info("    * " + s);

      log.info("Bounded peers: " + manager.getCachePeerListener(RMI_SCHEME).getBoundCachePeers());
      log.info("Remote peers: " + manager.getCacheManagerPeerProvider(RMI_SCHEME).listRemoteCachePeers(getCache(null)));

      log.debug("Finish Initializing the cache");
   }

   @Override
   public synchronized void stop() {
      manager.shutdown();
   }

   @Override
   public synchronized boolean isRunning() {
      return manager.getStatus() == Status.STATUS_ALIVE;
   }

   public Ehcache getCache(String cacheName) {
      return manager.getCache(cacheName);
   }

   @Override
   public boolean isCoordinator() {
      return false;
   }

   @Override
   public Collection<Member> getMembers() {
      ArrayList<Member> members = new ArrayList<>();
      for (Object peer: manager.getCacheManagerPeerProvider("RMI").listRemoteCachePeers(getCache(null))) {
         members.add(new Member(peer.toString(), false, false));
      }
      members.add(new Member("localhost", true, false));
      return members;
   }

   @Override
   public List<Membership> getMembershipHistory() {
      return Collections.EMPTY_LIST; //TODO
   }
}

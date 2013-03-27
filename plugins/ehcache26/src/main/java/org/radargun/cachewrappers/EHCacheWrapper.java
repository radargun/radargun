package org.radargun.cachewrappers;

import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.Status;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.features.AtomicOperationsCapable;
import org.radargun.features.BulkOperationsCapable;
import org.radargun.utils.TypedProperties;


/**
 * An implementation of SerializableCacheWrapper that uses EHCache as an underlying implementation.
 * <p/>
 * Pass in a -Dbind.address=IP_ADDRESS ehcache propery files allows referencing system properties through syntax
 * ${bind.address}.
 *
 * @author Manik Surtani (manik@surtani.org)
 */
public class EHCacheWrapper implements CacheWrapper, AtomicOperationsCapable, BulkOperationsCapable {
   private CacheManager manager;
   private Ehcache cache;
   private Log log = LogFactory.getLog("org.radargun.cachewrappers.EHCacheWrapper");
   boolean localMode;
   private String configFile, cacheName;

   public void setUp(String config, boolean isLocal, int nodeIndex, TypedProperties confAttributes) throws Exception {
      if (log.isTraceEnabled()) log.trace("Entering EHCacheWrapper.setUp()");
      localMode = isLocal;
      log.debug("Initializing the cache with props " + config);

      configFile  = confAttributes.containsKey("file") ? confAttributes.getProperty("file") : config;
      cacheName = confAttributes.containsKey("cache") ? confAttributes.getProperty("cache") : "x";


      log.debug("Initializing the cache with props " + config);
      URL url = getClass().getClassLoader().getResource(configFile);
      manager = new CacheManager(url);
      log.info("Caches available:");

      for (String s : manager.getCacheNames()) log.info("    * " + s);
      cache = manager.getCache(cacheName);

      log.info("Using named cache " + cache);
      if (!localMode) {
         log.info("Bounded peers: " + manager.getCachePeerListener("RMI").getBoundCachePeers());
         log.info("Remote peers: " + manager.getCacheManagerPeerProvider("RMI").listRemoteCachePeers(cache));
      }

      log.debug("Finish Initializing the cache");
   }

   public void tearDown() throws Exception {
      manager.shutdown();
   }

   @Override
   public boolean isRunning() {
      return manager.getStatus() == Status.STATUS_ALIVE;
   }

   public void putSerializable(Serializable key, Serializable value) throws Exception {
      Element element = new Element(key, value);
      cache.put(element);
   }

   public Object getSerializable(Serializable key) throws Exception {
      return cache.get(key);
   }

   public void empty() throws Exception {
      cache.removeAll();
   }

   public void put(String path, Object key, Object value) throws Exception {
      putSerializable((Serializable) key, (Serializable) value);
   }

   public Object get(String bucket, Object key) throws Exception {
      Object s = getSerializable((Serializable) key);
      if (s instanceof Element) {
         return ((Element) s).getValue();
      } else return s;
   }

   @Override
   public Object remove(String bucket, Object key) throws Exception {
      return cache.remove(key);
   }

   @Override
   public boolean replace(String bucket, Object key, Object oldValue, Object newValue) throws Exception {
      Serializable sKey = (Serializable) key;
      Element oldElement = new Element(sKey, (Serializable) oldValue);
      Element newElement = new Element(sKey, (Serializable) newValue);
      return cache.replace(oldElement, newElement);
   }

   @Override
   public Object putIfAbsent(String bucket, Object key, Object value) throws Exception {
      Element element = new Element((Serializable) key, (Serializable) value);
      Element previous = cache.putIfAbsent(element);
      return previous.getValue();
   }

   @Override
   public boolean remove(String bucket, Object key, Object oldValue) throws Exception {
      Element element = new Element((Serializable) key, (Serializable) oldValue);
      return cache.removeElement(element);
   }

   public int getNumMembers() {
      return localMode ? 0 : manager.getCacheManagerPeerProvider("RMI").listRemoteCachePeers(cache).size();
   }

   public String getInfo() {
      return "EHCache " + (localMode ? "" : (" remote peers: " + manager.getCachePeerListener("RMI").getBoundCachePeers())) + ", config: " + configFile + ", cacheName: " + cacheName;
   }

   public Object getReplicatedData(String path, String key) throws Exception {
      Object o = get(path, key);
      if (log.isTraceEnabled()) {
         log.trace("Result for the key: '" + key + "' is value '" + o + "'");
      }
      return o;
   }

   @Override
   public boolean isTransactional(String bucket) {
      return false;
   }

   public void startTransaction() {
      throw new UnsupportedOperationException("Does not support JTA!");
   }

   public void endTransaction(boolean successful) {
      throw new UnsupportedOperationException("Does not support JTA!");
   }

   @Override
   public int getLocalSize() {
      return cache.getKeys().size();
   }

   @Override
   public int getTotalSize() {
      return -1;
   }

   @Override
   public Map<Object, Object> getAll(String bucket, Set<Object> keys, boolean preferAsync) throws Exception {
      Map<Object, Object> map = new HashMap<Object, Object>();
      Map<Object, Element> elements = cache.getAll(keys);
      for (Element element : elements.values()) {
         map.put(element.getObjectKey(), element.getObjectValue());
      }
      return map;
   }

   @Override
   public Map<Object, Object> putAll(String bucket, Map<Object, Object> entries, boolean preferAsync) throws Exception {
      ArrayList<Element> elements = new ArrayList<Element>(entries.size());
      for (Map.Entry<Object, Object> entry : entries.entrySet()) {
         elements.add(new Element(entry.getKey(), entry.getValue()));
      }
      cache.putAll(elements);
      return null;
   }

   @Override
   public Map<Object, Object> removeAll(String bucket, Set<Object> keys, boolean preferAsync) throws Exception {
      cache.removeAll(keys);
      return null;
   }
}

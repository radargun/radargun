package org.cachebench.cachewrappers;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.ConfigurationFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;

import java.io.Serializable;
import java.net.URL;


/**
 * An implementation of SerializableCacheWrapper that uses EHCache as an underlying implementation.
 * <p/>
 * Pass in a -Dbind.address=IP_ADDRESS
 * ehcache propery files allows referencing system properties through syntax ${bind.address}.
 *
 * @author Manik Surtani (manik@surtani.org)
 * @version $Id: EHCacheWrapper.java,v 1.6 2007/05/21 16:17:56 msurtani Exp $
 */
public class EHCacheWrapper implements CacheWrapper
{
   private CacheManager manager;
   private Ehcache cache;
   private Log log = LogFactory.getLog("org.cachebench.cachewrappers.EHCacheWrapper");
   boolean localmode;

   /* (non-Javadoc)
   * @see org.cachebench.CacheWrapper#setUp(java.util.Properties)
   */
   public void setUp(String config, boolean isLocal, int nodeIndex) throws Exception
   {
      if (log.isTraceEnabled()) log.trace("Entering EHCacheWrapper.setUp()");
      localmode = isLocal;
      log.debug("Initializing the cache with props " + config);
      URL url = getClass().getClassLoader().getResource(config);
      log.debug("Config URL = " + url);
      Configuration c = ConfigurationFactory.parseConfiguration(url);
      c.setSource("URL of " + url);

      manager = new CacheManager(c);
       log.info("Caches avbl:");
       for (String s : manager.getCacheNames()) log.info("    * " + s);
       cache = manager.getCache("cache");
       log.info("Using named cache " + cache);
       if (!localmode)
       {
          log.info("Bounded peers: " + manager.getCachePeerListener("RMI").getBoundCachePeers());
          log.info("Remote peers: " + manager.getCacheManagerPeerProvider("RMI").listRemoteCachePeers(cache));
       }
      
      log.debug("Finish Initializing the cache");
   }

   /* (non-Javadoc)
   * @see org.cachebench.CacheWrapper#setUp()
   */

   /* (non-Javadoc)
   * @see org.cachebench.CacheWrapper#tearDown()
   */
   public void tearDown() throws Exception
   {
      manager.shutdown();
   }

   /* (non-Javadoc)
   * @see org.cachebench.SerializableCacheWrapper#putSerializable(java.io.Serializable, java.io.Serializable)
   */
   public void putSerializable(Serializable key, Serializable value) throws Exception
   {
      Element element = new Element(key, value);
      cache.put(element);
   }

   /* (non-Javadoc)
   * @see org.cachebench.SerializableCacheWrapper#getSerializable(java.io.Serializable)
   */
   public Object getSerializable(Serializable key) throws Exception
   {
      return cache.get(key);
   }

   public void empty() throws Exception
   {
      cache.removeAll();
   }

   /* (non-Javadoc)
   * @see org.cachebench.CacheWrapper#put(java.lang.Object, java.lang.Object)
   */
   public void put(String path, Object key, Object value) throws Exception
   {
      putSerializable((Serializable) key, (Serializable) value);
   }

   /* (non-Javadoc)
   * @see org.cachebench.CacheWrapper#get(java.lang.Object)
   */
   public Object get(String bucket, Object key) throws Exception
   {
      Object s = getSerializable((Serializable) key);
      if (s instanceof Element)
      {
         return ((Element) s).getValue();
      }
      else return s;
   }

   public int getNumMembers()
   {

      return localmode ? 0 : manager.getCacheManagerPeerProvider("RMI").listRemoteCachePeers(cache).size();
   }

   public String getInfo()
   {
      return cache.getKeys().toString() + (localmode ? "" : (" remote peers: " + manager.getCachePeerListener("RMI").getBoundCachePeers()));
   }

   public Object getReplicatedData(String path, String key) throws Exception
   {
      Object o = get(path, key);
      if (log.isTraceEnabled())
      {
         log.trace("Result for the key: '" + key + "' is value '" + o + "'");
      }
      return o;
   }

   public Object startTransaction()
   {
      throw new UnsupportedOperationException("Does not support JTA!");
   }

   public void endTransaction(boolean successful)
   {
      throw new UnsupportedOperationException("Does not support JTA!");
   }
}

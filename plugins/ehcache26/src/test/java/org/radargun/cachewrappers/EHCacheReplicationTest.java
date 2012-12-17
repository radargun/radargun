/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.radargun.cachewrappers;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.Ehcache;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test (enabled = false)
public class EHCacheReplicationTest
{
   String cfgFile = "/path/to/file.xml";
   @BeforeMethod
   public void setUp()
   {
      System.setProperty("bind.address","127.0.0.1");//bind address referenced from config file
   }


   public void testSyncReplication() throws Exception
   {
      Ehcache cache1;
      Ehcache cache2;



      CacheManager c1 = new CacheManager(cfgFile);
      CacheManager c2 = new CacheManager(cfgFile);


      cache1 = c1.getCache("cache");
      cache2 = c2.getCache("cache");

      Thread.sleep(5000);

      System.out.println("c1 members: " + c1.getCachePeerListener("").getBoundCachePeers());
      System.out.println("c2 members" + c2.getCachePeerListener("").getBoundCachePeers());
      assert c1.getCachePeerListener("").getBoundCachePeers().size() == 1;
      assert c2.getCachePeerListener("").getBoundCachePeers().size() == 1;

      for (int i = 0; i < 100; i++)
      {
         cache1.put(new Element("key" + i, "value" + i));
         assert cache2.get("key" + i).getValue().equals("value" + i);
      }
      System.out.println(cache1.getKeys());
      System.out.println(cache2.getKeys());

      c1.shutdown();
      c2.shutdown();
   }


   public void testInvalidation() throws Exception 
   {
      Ehcache cache1;
      Ehcache cache2;

      CacheManager c1 = new CacheManager(cfgFile);
      CacheManager c2 = new CacheManager(cfgFile);

      cache1 = c1.getCache("cache");
      cache2 = c2.getCache("cache");

      Thread.sleep(5000);

      System.out.println("c1 members: " + c1.getCachePeerListener("").getBoundCachePeers());
      System.out.println("c2 members" + c2.getCachePeerListener("").getBoundCachePeers());
      assert c1.getCachePeerListener("").getBoundCachePeers().size() == 1;
      assert c2.getCachePeerListener("").getBoundCachePeers().size() == 1;

      cache1.put(new Element("key","value"));
      assert cache2.get("key").getValue().equals("value");
      cache2.put(new Element("key","newValue"));
      assert cache1.get("key") == null;

      c1.shutdown();
      c2.shutdown();
   }
}

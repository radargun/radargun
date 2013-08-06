/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

import java.io.FileNotFoundException;
import java.io.InputStream;

import org.infinispan.Cache;
import org.infinispan.commons.util.FileLookupFactory;
import org.infinispan.configuration.cache.ClusteringConfiguration;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Infinispan60Wrapper extends InfinispanXSWrapper {
   @Override
   protected ConfigurationBuilderHolder createConfiguration(String configFile) throws FileNotFoundException {
      InputStream input = FileLookupFactory.newInstance().lookupFileStrict(configFile, Thread.currentThread().getContextClassLoader());
      return new ParserRegistry(Thread.currentThread().getContextClassLoader()).parse(input);
   }

   @Override
   protected boolean isCacheDistributed(Cache<Object, Object> cache) {
      ClusteringConfiguration clustering = cache.getCacheConfiguration().clustering();
      return clustering != null && clustering.cacheMode().isDistributed();
   }
}

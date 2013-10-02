/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
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
 * @author Alan Field &lt;afield@redhat.com&gt;
 */

public class Infinispan60DistributedTaskWrapper<K, V, T> extends InfinispanDistributedTaskWrapper<K, V, T> {
   @Override
   protected ConfigurationBuilderHolder createConfiguration(String configFile) throws FileNotFoundException {
      InputStream input = FileLookupFactory.newInstance().lookupFileStrict(configFile,
            Thread.currentThread().getContextClassLoader());
      return new ParserRegistry(Thread.currentThread().getContextClassLoader()).parse(input);
   }

   @Override
   protected boolean isCacheDistributed(Cache<Object, Object> cache) {
      ClusteringConfiguration clustering = cache.getCacheConfiguration().clustering();
      return clustering != null && clustering.cacheMode().isDistributed();
   }
}

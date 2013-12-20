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

import org.infinispan.commons.util.FileLookupFactory;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.container.entries.InternalCacheEntry;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @author Michal Linhard (mlinhard@redhat.com)
 */
public class Infinispan60Wrapper extends Infinispan53Wrapper {
   @Override
   protected ConfigurationBuilderHolder createConfiguration(String configFile) throws FileNotFoundException {
      InputStream input = FileLookupFactory.newInstance().lookupFileStrict(configFile, Thread.currentThread().getContextClassLoader());
      return new ParserRegistry(Thread.currentThread().getContextClassLoader()).parse(input);
   }

   @Override
   protected String toString(InternalCacheEntry ice) {
      if (ice == null) return null;
      StringBuilder sb = new StringBuilder(256);
      sb.append(ice.getClass().getSimpleName());
      sb.append("[key=").append(ice.getKey()).append(", value=").append(ice.getValue());
      sb.append(", created=").append(ice.getCreated()).append(", isCreated=").append(ice.isCreated());
      sb.append(", lastUsed=").append(ice.getLastUsed()).append(", isChanged=").append(ice.isChanged());
      sb.append(", expires=").append(ice.getExpiryTime()).append(", isExpired=").append(ice.isExpired(System.currentTimeMillis()));
      sb.append(", canExpire=").append(ice.canExpire()).append(", isEvicted=").append(ice.isEvicted());
      sb.append(", isRemoved=").append(ice.isRemoved()).append(", isValid=").append(ice.isValid());
      sb.append(", lifespan=").append(ice.getLifespan()).append(", maxIdle=").append(ice.getMaxIdle());
      return sb.append(']').toString();
   }

   protected ConfigDumpHelper createConfigDumpHelper() {
      return new ConfigDumpHelper60();
   }   
}

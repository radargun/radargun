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
package org.radargun.features;

import java.util.Collection;
import java.util.List;

import org.radargun.CacheWrapper;

/**
 * Cache wrapper that is able of cross-site replication and, therefore, carries
 * multiple caches (then accessed directly using the bucket argument in other operations)
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface XSReplicating extends CacheWrapper {
   /**
    * @return Name of the cache where we execute the common operations. Null can be used as bucket to access this cache.
    */
   String getMainCache();

   /**
    * @return Names of the other caches which should be read-only, because these are filled from different site.
    */
   Collection<String> getBackupCaches();

   /**
    * @return Slaves which are in the same site as this slave
    */
   List<Integer> getSlaves();

   /**
    * @return True if this slave directly routes traffic into other sites.
    */
   boolean isBridge();
}

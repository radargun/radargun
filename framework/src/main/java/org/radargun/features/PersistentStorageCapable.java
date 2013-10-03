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

package org.radargun.features;

/**
 * Feature for wrappers that support persistent storage.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface PersistentStorageCapable {
   /**
    * Retrieve value for this bucket and key, ignoring any persistent storage.
    *
    * @param bucket
    * @param key
    * @return
    * @throws Exception
    */
   Object getMemoryOnly(String bucket, Object key) throws Exception;

   /**
    * Write entry into the cache but do not propagate the write into underlying persistent storage.
    *
    * @param bucket
    * @param key
    * @param value
    * @return
    * @throws Exception
    */
   Object putMemoryOnly(String bucket, Object key, Object value) throws Exception;

   /**
    * Remove entry from the cache but do not remove it from the underlying persistent storage.
    *
    * @param bucket
    * @param key
    * @return
    * @throws Exception
    */
   Object removeMemoryOnly(String bucket, Object key) throws Exception;
}

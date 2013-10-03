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

import java.util.Set;

/**
 * Allows the wrapper to simulate partition split.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface Partitionable {
   /**
    * Changes with which members this slave can communicate. Should be run only when the wrapper is started.
    *
    * @param slaveIndex Index of this slave
    * @param members Index of slaves with which this slave can communicate
    */
   void setMembersInPartition(int slaveIndex, Set<Integer> members);

   /**
    * Allows to set the partition before the wrapper is started.
    *
    * @param slaveIndex Index of this slave
    * @param members Index of slaves with which this slave can communicate
    */
   void setStartWithReachable(int slaveIndex, Set<Integer> members);
}

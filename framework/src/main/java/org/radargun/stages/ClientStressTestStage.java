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
package org.radargun.stages;

import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stressors.ClientStressTestStressor;

import java.util.Map;

/**
 * Repeats the StressTest logic with variable amount of threads.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Repeats the StressTest logic with increasing amount of client threads.")
public class ClientStressTestStage extends StressTestStage {

   @Property(optional = false, doc = "Initial number of threads.")
   private int initThreads = 1;

   @Property(optional = false, doc = "Maximum number of threads this will be run with.")
   private int maxThreads = 10;

   @Property(doc = "Number of threads which should be added in each iteration. Default is 1.")
   private int increment = 1;
   
   @Override   
   protected Map<String, String> doWork() {
      log.info("Starting " + getClass().getSimpleName() + ": " + this);
      ClientStressTestStressor putGetStressor = new ClientStressTestStressor();
      putGetStressor.setNodeIndex(getSlaveIndex());
      putGetStressor.setNumEntries(numEntries);
      putGetStressor.setNumRequests(numRequests);
      putGetStressor.setInitThreads(initThreads);
      putGetStressor.setMaxThreads(maxThreads);
      putGetStressor.setIncrement(increment);
      putGetStressor.setOpsCountStatusLog(opsCountStatusLog);
      putGetStressor.setEntrySize(entrySize);
      putGetStressor.setWritePercentage(writePercentage);
      putGetStressor.setKeyGeneratorClass(keyGeneratorClass);
      putGetStressor.setUseTransactions(useTransactions);
      putGetStressor.setCommitTransactions(commitTransactions);
      putGetStressor.setTransactionSize(transactionSize);
      putGetStressor.setDurationMillis(duration);
      return putGetStressor.stress(cacheWrapper);
   }
}

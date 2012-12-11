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

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.helpers.KillHelper;
import org.radargun.stages.helpers.ParseHelper;
import org.radargun.stages.helpers.RoleHelper;
import org.radargun.stages.helpers.StartHelper;

import java.util.*;

/**
 * The stage start and kills some nodes concurrently (without waiting for each other).
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "The stage start and kills some nodes concurrently (without waiting for each other).")
public class ParallelStartKillStage extends AbstractStartStage {

   @Property(doc = "Set of slaves which should be killed in this stage. Default is empty.")
   private Collection<Integer> kill = new ArrayList<Integer>();

   @Property(doc = "Set of slaves which should be started in this stage. Default is empty.")
   private Collection<Integer> start = new ArrayList<Integer>();

   @Property(doc = "Applicable only for cache wrappers with Partitionable feature. Set of slaves that should be" +
         "reachable from the new node. Default is empty.")
   private Set<Integer> reachable = new HashSet<Integer>();

   /* Note: having role for start has no sense as the dead nodes cannot have any role in the cluster */
   @Property(doc = "Another way how to specify killed nodes is by role. Available roles are "
         + RoleHelper.SUPPORTED_ROLES + ". By default this is not used.")
   private String role;
   
   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      boolean killMe = kill.contains(getSlaveIndex()) || RoleHelper.hasRole(slaveState, role);
      boolean startMe = start.contains(getSlaveIndex());
      if (!(killMe || startMe)) {
         log.info("Nothing to kill or start...");
      }
      while (killMe || startMe) {
         if (startMe) {
            if (slaveState.getCacheWrapper() != null) {
               if (!killMe) {
                  log.info("Wrapper already set on this slave, not starting it again.");
                  startMe = false;
                  return ack;
               } 
            } else {
               StartHelper.start(productName, config, confAttributes, slaveState, getSlaveIndex(),
                     null, reachable, classLoadHelper, ack);
               if (ack.isError()) return ack;
               startMe = false;               
            }            
         }
         if (killMe) {
            if (slaveState.getCacheWrapper() == null) {
               if (!startMe) {
                  log.info("Wrapper is dead, nothing to kill");
                  killMe = false;
                  return ack;
               }
            } else {
               KillHelper.kill(slaveState, false, false, ack);
               if (ack.isError()) return ack;
               killMe = false;
            }
         }
      }
      return ack;
   }

   @Override
   public void setSlaves(String slaves) {
      log.warn("Slaves attribute is deprecated for this stage, use kill or start instead");
   }
   
   public void setKill(String killString) {
      kill = ParseHelper.parseSet(killString, "kill", log);
   }
   
   public void setStart(String startString) {
      start = ParseHelper.parseSet(startString, "start", log);
   }
   
   public void setRole(String role) {
      this.role = role;
   }
   
   public void setReachable(String reachable) {
      Set<Integer> r = new HashSet<Integer>();
      StringTokenizer tokenizer = new StringTokenizer(reachable, ",");
      try {
         while (tokenizer.hasMoreTokens()) {
            r.add(Integer.parseInt(tokenizer.nextToken().trim()));
         }
      } catch (NumberFormatException e) {
         log.error("Failed to parse slave list " + reachable);
      }
      this.reachable = r;
   }
   
   public String str(Collection<Integer> list) {
      StringBuilder sb = new StringBuilder();
      for (int element : list) {
         sb.append(element);
         sb.append(", ");
      }
      return sb.toString();
   }
   
   @Override
   public String toString() {
      return "ParallelStartKillStage(start=" + str(start) + "kill=" + str(kill) + super.toString();
   }
}

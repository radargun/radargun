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

import java.util.ArrayList;
import java.util.List;

import org.radargun.DistStageAck;

public class ParallelStartKillStage extends AbstractStartStage {

   private List<Integer> kill = new ArrayList<Integer>();
   private List<Integer> start = new ArrayList<Integer>();
   
   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      boolean killMe = kill.contains(getSlaveIndex());
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
                     false, 0, classLoadHelper, ack);
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
               KillHelper.kill(slaveState, false, ack);
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
      setList(kill, killString);
   }
   
   public void setStart(String startString) {
      setList(start, startString);
   }
   
   private void setList(List<Integer> list, String listString) {
      list.clear();
      try {
         for (String id : listString.split(",")) {
            list.add(Integer.parseInt(id));
         }
      } catch (NumberFormatException e) {
         log.error("Cannot parse " + listString + " as a list of slaves", e);
      }
   }
   
   public String str(List<Integer> list) {
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

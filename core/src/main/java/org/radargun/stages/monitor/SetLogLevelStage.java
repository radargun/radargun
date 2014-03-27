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
package org.radargun.stages.monitor;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.logging.Level;
import org.radargun.logging.LogFactory;
import org.radargun.stages.AbstractDistStage;

/**
 * This stage is meant for debugging. Changes log priorities. Beware that some code can cache the is{LogLevel}Enabled().
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Debugging stage: changes log priorities")
public class SetLogLevelStage extends AbstractDistStage {

   @Property(optional = false, name="package", doc = "The package or class which should be affected.")
   private String pkg;

   @Property(doc = "The new priority that should be used. No defaults.")
   private String priority;

   @Property(doc = "If set to true, instead of setting the priority directly just undo the last priority change. Default is false.")
   private boolean pop;

   private static Map<String, Stack<Level>> stacks = new HashMap<String, Stack<Level>>();

   @Override
   public DistStageAck executeOnSlave() {
      try {
         Stack<Level> stack = stacks.get(pkg);
         if (stack == null) {
            stack = new Stack<Level>();
            stacks.put(pkg, stack);
         }
         if (priority != null) {
            stack.push(LogFactory.getLog(pkg).getLevel());
            LogFactory.getLog(pkg).setLevel(Level.toLevel(priority));
         } else if (pop) {
            if (stack.empty()) {
               log.error("Cannot POP priority level, stack empty!");
            } else {
               LogFactory.getLog(pkg).setLevel(stack.pop());
            }
         } else {
            log.error("Neither priority nor POP request specified");
         }
      } catch (Exception e) {
         log.error("Failed to change log level", e);
      }
      return newDefaultStageAck();
   }
}

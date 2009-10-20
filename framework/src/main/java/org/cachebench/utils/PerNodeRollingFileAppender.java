/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.cachebench.utils;

import org.apache.log4j.RollingFileAppender;
import org.cachebench.config.ClusterConfig;

/**
 * Apends an node instance identifier at the end of the filename.
 *
 * @author Mircea.Markus@jboss.com
 */
public class PerNodeRollingFileAppender extends RollingFileAppender
{
   ClusterConfig conf = new ClusterConfig();

   @Override
   public void setFile(String s) {
      super.setFile(appendNodeIndex(s));
   }

   private String appendNodeIndex(String s) {
      try
      {
         return conf.getCurrentNodeIndex() + "_" + s;
      } catch (Throwable e)//ignore here, we're just a logger
      {
         System.out.println("exception occued while trying to index the file name: " + e.getMessage());
         return s;
      }
   }


}

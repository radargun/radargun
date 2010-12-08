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
package org.radargun.utils;

import org.apache.log4j.RollingFileAppender;

/**
 * Apends an node instance identifier at the end of the filename.
 *
 * @author Mircea.Markus@jboss.com
 */
public class PerNodeRollingFileAppender extends RollingFileAppender {

   public static final String PROP_NAME = "log4j.file.prefix";

   @Override
   public void setFile(String s) {
      super.setFile(appendNodeIndex(s));
   }

   private String appendNodeIndex(String s) {
      String prop = System.getProperty(PROP_NAME);
      if (prop != null) {
         System.out.println("PerNodeRollingFileAppender::Using file prefix:" + prop);
         return prop + "_" + s;
      } else {
         System.out.println("PerNodeRollingFileAppender::Not using file prefix.");
         return s;
      }
   }

}

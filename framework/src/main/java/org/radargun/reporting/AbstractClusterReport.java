/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other contributors
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
package org.radargun.reporting;

import java.util.ArrayList;
import java.util.List;

/**
 * Object to store data for a cluster wide chart
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public class AbstractClusterReport {

   private String xLabel;
   private String yLabel;
   private String title;
   private String subtitle;
   private List<String> notes = new ArrayList<String>();

   public AbstractClusterReport() {
      super();
   }

   public void init(String xLabel, String yLabel, String title, String subtitle) {
      this.xLabel = xLabel;
      this.yLabel = yLabel;
      this.title = title;
      this.subtitle = subtitle;
   }

   public void addNote(String note) {
      notes.add(note);
   }

   public String getTitle() {
      return title;
   }

   public String getSubtitle() {
      return subtitle;
   }

   public String getXLabel() {
      return xLabel;
   }

   public String getYLabel() {
      return yLabel;
   }

   public List<String> getNotes() {
      return notes;
   }

}
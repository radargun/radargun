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
package org.radargun.reporting;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.jfree.data.category.DefaultCategoryDataset;

public class HtmlReportGenerator {
   public static void generate(ClusterReport report, String reportDir, String fileName) throws IOException {
      File dir = new File(reportDir);
      if (!dir.exists()) dir.mkdir();
      
      PrintWriter writer = null;
      try {
         writer = new PrintWriter(reportDir + File.separator + fileName + ".html");
         writer.print("<HTML><HEAD><TITLE>");
         writer.print(report.getTitle());
         writer.print("</TITLE></HEAD>\n<BODY><H1>");
         writer.print(report.getTitle());
         writer.println("</H1>");
         writer.print("<TABLE>\n<TR><TH>&nbsp;</TH>");
         DefaultCategoryDataset set = report.getCategorySet();
         for (Object key : set.getColumnKeys()) {
            writer.print("<TH>");
            writer.print(key.toString());
            writer.print("</TH>");
         }
         writer.println("</TR>");
         for (int i = 0; i < set.getRowCount(); ++i) {
            writer.print("<TR><TH>");
            writer.print(set.getRowKey(i));
            writer.print("</TH>");
            for (int j = 0; j < set.getColumnCount(); ++j) {
               writer.print("<TD>");
               writer.print(humanFormat(set.getValue(i, j)));
               writer.print("</TD>");
            }
            writer.println("</TR>");
         }
         writer.println("</TABLE>");
         writer.println("<H4>Notes:</H4><UL>");
         for (String note : report.getNotes()) {
            writer.print("<LI>");
            writer.print(note);
            writer.println("</LI>");
         }
         writer.println("</UL>");
         writer.print("<br><br><br><small>");
         writer.print(report.getSubtitle());
         writer.println("</small>");
         writer.println("</HTML>");         
      } finally {
         if (writer != null) writer.close();
      }
   }

   private static String humanFormat(Object o) {
      if (o instanceof Double) {
         return String.format("%.2f", o);
      }
      return o.toString();
   }
}

package org.radargun.reporting.html;

import java.io.File;

/**
 * Base class for formatting HTML file.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class HtmlDocument {
   protected final String directory;
   private final String title;
   private final String fileName;

   /**
    * Start a new document in given directory with specified filename and title.
    * The file is not created yet upon construction of this object.
    *
    * @param directory
    * @param fileName
    * @param title
    */
   public HtmlDocument(String directory, String fileName, String title) {
      this.directory = directory;
      this.fileName = fileName;
      this.title = title;
   }

   public String getFileName() {
      return fileName;
   }

   public String getDirectory() {
      return directory;
   }

   public void createReportDirectory() {
      File dir = new File(directory);
      dir.mkdirs();
   }

   /**
    * The following methods are used in Freemarker templates
    * e.g. method getPercentiles() can be used as getPercentiles() or percentiles in template
    */

   public String getTitle() {
      return title;
   }
}

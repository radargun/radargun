package org.radargun.reporting.html;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Base class for formatting HTML file.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class HtmlDocument {
   protected PrintWriter writer;
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

   /**
    * Open the document file and write common headers.
    * @throws IOException
    */
   public void open() throws IOException {
      File dir = new File(directory);
      if (dir.exists() && !dir.isDirectory()) {
         throw new IllegalArgumentException(dir.getAbsolutePath() + " is not a directory");
      } else if (!dir.exists()) {
         dir.mkdirs();
      }
      writer = new PrintWriter(directory + File.separator + fileName);
      write("<HTML><HEAD><TITLE>");
      write(title);
      write("</TITLE><STYLE>\n");
      writeStyle();
      write("</STYLE>\n<SCRIPT>\n");
      writeScripts();
      write("</SCRIPT></HEAD>\n<BODY>");
   }

   /**
    * Write CSS styles.
    */
   protected void writeStyle() {
   }

   /**
    * Write JavaScript scripts used in the document.
    */
   protected void writeScripts() {
   }

   /**
    * Write footer and close the file.
    */
   public void close() {
      writer.println("</BODY></HTML>");
      writer.close();
   }

   /**
    * Write open tag, content and closing tag
    * @param tag
    * @param content
    */
   protected void writeTag(String tag, String content) {
      writer.write(String.format("<%s>%s</%s>", tag, content, tag));
   }

   /**
    * Write arbitrary text.
    * @param text
    */
   public void write(String text) {
      writer.write(text);
   }
}

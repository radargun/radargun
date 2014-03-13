package org.radargun.reporting.html;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class HtmlDocument {
   protected PrintWriter writer;
   protected final String directory;
   private final String title;
   private final String fileName;

   public HtmlDocument(String directory, String fileName, String title) {
      this.directory = directory;
      this.fileName = fileName;
      this.title = title;
   }

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

   protected void writeStyle() {
   }

   protected void writeScripts() {
   }

   public void close() {
      writer.println("</BODY></HTML>");
      writer.close();
   }

   protected void writeTag(String tag, String content) {
      writer.write(String.format("<%s>%s</%s>", tag, content, tag));
   }

   public void write(String text) {
      writer.write(text);
   }
}

package org.cachebench.reportgenerators;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.tests.results.TestResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

/**
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
public abstract class CsvBaseReportGenerator extends AbstractReportGenerator
{
   protected static Log log = LogFactory.getLog(CsvBaseReportGenerator.class); 

   private ArrayList footNotes;

   public void generate() throws Exception
   {
      try
      {
         BufferedWriter writer = null;

         log.debug("Opening output file [" + output + "]");
         prepareReportFile();
         writer = new BufferedWriter(new FileWriter(output));
         writeHeaderLine(writer);

         for (TestResult result : results)
         {
            writeTestResult(result, writer);
            checkForFootnotes(result);
         }
         // Write the Footnotes (if available)
         if (footNotes != null)
         {
            writeFoodNotes(writer);
         }
         writer.close();
         log.debug("Report complete");
         if  (output.exists())
         {
            log.warn("Expected report file:'" + output.getAbsoluteFile() + "'does not exist!");
         }
      } catch (IOException e)
      {
         log.error("Error appeared while generatin report:", e);
      }
   }

   private void checkForFootnotes(TestResult result)
   {
      // Now check if we have foot notes for this error
      if (!"".equals(result.getFootNote()))
      {
         // We hae footnotes
         if (footNotes == null)
         {
            footNotes = new ArrayList();
         }
         footNotes.add(result.getFootNote());
         log.debug("Foot node found, added " + result.getFootNote());
      }
   }

   protected abstract void writeTestResult(TestResult result, BufferedWriter writer) throws IOException;

   protected abstract void writeHeaderLine(BufferedWriter writer) throws IOException;

   private void prepareReportFile() throws IOException
   {
      String fileName = output.getAbsolutePath() + ".old." + System.currentTimeMillis();
      if (output.exists())
      {
         log.info("A file named: '" + output.getAbsolutePath() + "' already exist. Renaming to '" + fileName + "'");
         if (output.renameTo(new File(fileName))) {
            log.warn("Could not rename!!!");
         }
      } else
      {
         if (output.createNewFile()) {
            log.info("Successfully created report file:" + output.getAbsolutePath());
         } else {
            log.warn("Failed to create the report file!");
         }
      }
   }

   private void writeFoodNotes(BufferedWriter writer) throws IOException
   {
      log.debug("Writing the Footnotes");
      writer.newLine();
      writer.newLine();
      writer.newLine();
      writer.newLine();
      writer.write("Report FootNotes");
      writer.newLine();
      int footNoteSize = footNotes.size();
      for (int i = 0; i < footNoteSize; i++)
      {
         writer.write((String) footNotes.get(i));
         writer.newLine();
      }
      log.debug("Complted the Footnotes");
   }


}

package org.radargun.config;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.net.URL;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * Responsible to parse `xslt(file_path, xslt_path)` into a file.
 *
 * The xslt function could have a third parameter that is the path where the file will be store `xslt(file_path, xslt_path, output_path)`
 *
 *
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
public class XsltConverter implements Converter<String> {

   private static final Log log = LogFactory.getLog(XsltConverter.class);

   private static final String XSLT_SEPARATOR = ",";
   private static final String XSLT_PREFIX = "xslt(";


   /**
    * Return true if the parameter is function to parse the XML using XSLT
    * @param file
    *    xslt(file_path, xslt_path) is an expression that parse the file using the XSLT. Return true
    *    foo/bar.xml is a full path. Return false
    * @return true if is an operation to parse the XML using XSLT
    */
   public boolean isXsltAttribute(String file) {
      boolean xslt = false;
      if (file != null && !file.trim().isEmpty()) {
         xslt = file.toLowerCase().startsWith(XSLT_PREFIX);
      }
      return xslt;
   }

   /**
    * Return file wrote with the result from XSLT transformation
    * @param file Only support xslt(file_path, xslt_path). The method @isXsltAttribute should be called first
    * @return file filled with the result from XSLT transformation
    */
   @Override
   public String convert(String file, Type type) {
      return convertToString(file);
   }

   /**
    * Return file wrote with the result from XSLT transformation
    * @param file Only support xslt(file_path, xslt_path). The method @isXsltAttribute should be called first
    * @return file filled with the result from XSLT transformation
    */
   @Override
   public String convertToString(String file) {
      String[] data;
      File inputFile;
      File xsltFile;
      File outputFile;

      try {
         data = file.substring(XSLT_PREFIX.length(), file.length() - 1).split(XSLT_SEPARATOR);
      } catch (Exception e) {
         throw new IllegalStateException(file + " does not match the pattern: xslt(file_path, xslt_path)", e);
      }

      inputFile = getFile(data[0]);
      xsltFile = getFile(data[1]);
      if (data.length == 3) {
         outputFile = createOutputFile(getFile(data[2]).getAbsolutePath());
      } else {
         outputFile = createOutputFile(inputFile.getParentFile().getAbsolutePath());
      }

      log.info(String.format("The file '%s' will be transformed using the xslt '%s'", inputFile, xsltFile));

      TransformerFactory factory = TransformerFactory.newInstance();
      Source xslt = new StreamSource(xsltFile);
      Transformer transformer;
      try {
         transformer = factory.newTransformer(xslt);
      } catch (TransformerConfigurationException e) {
         throw new IllegalStateException("Error creating transformer using file " + xsltFile, e);
      }

      Source text = new StreamSource(inputFile);
      try {
         transformer.transform(text, new StreamResult(outputFile));
      } catch (TransformerException e) {
         throw new IllegalStateException("Error transforming the xml file", e);
      }
      return outputFile.getAbsolutePath();
   }

   @Override
   public String allowedPattern(Type type) {
      return ".*";
   }

   /**
    * Return the file first from the classpath evaluating the string first.
    * It is public because of the test.
    * @param filePath a full path of the file
    * @return the file from path
    */
   public static File getFile(String filePath) {
      filePath = Evaluator.parseString(filePath.trim());
      try {
         File file;
         URL url = XsltConverter.class.getResource("/" + filePath);
         if (url != null) {
            file = new File(url.toURI());
         } else {
            file = new File(filePath);
         }
         return file;
      } catch (URISyntaxException e) {
         throw new IllegalStateException("Cannot get the file: " + filePath, e);
      }
   }

   private static File createOutputFile(String parent) {
      File parentFolder = new File(parent);
      parentFolder.mkdirs();

      File outputFile = new File(parentFolder, "output.xml");
      if (outputFile.exists()) {
         outputFile.delete();
      }
      try {
         outputFile.createNewFile();
      } catch (IOException e) {
         throw new IllegalStateException("File '" + outputFile.getAbsolutePath() + "'not created", e);
      }
      return outputFile;
   }
}

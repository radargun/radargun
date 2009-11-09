package org.cachebench.fwk.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.config.ConfigBuilder;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;

/**
 * generates a configuration, fully validated.
 *
 * @author Manik Surtani
 */
public class ConfigFactory {

   private static final Log log = LogFactory.getLog(ConfigFactory.class);

   // can be overridden with -Dcachebench.config.validate=true.  False by default.
   boolean validating = Boolean.parseBoolean(System.getProperty("cachebench.config.validate", "false"));

   /**
    * File separator value
    */
   private static final String FILE_SEPARATOR = File.separator;

   /**
    * Path separator value
    */
   private static final String PATH_SEPARATOR = File.pathSeparator;

   /**
    * File separator alias
    */
   private static final String FILE_SEPARATOR_ALIAS = "/";

   /**
    * Path separator alias
    */
   private static final String PATH_SEPARATOR_ALIAS = ":";

   // States used in property parsing
   private static final int NORMAL = 0;
   private static final int SEEN_DOLLAR = 1;
   private static final int IN_BRACKET = 2;


   /**
    * Creates a LocalModeConfig.
    *
    * @param file either an absolute or relative path, or a name on the classpath
    * @return a LocalModeConfig bean
    */
   public LocalModeConfig createLocalModeConfig(String file) throws CannotLocateConfigException, ConfigParsingException {
      return (LocalModeConfig) parse(file);
   }

   /**
    * Creates a BenchConfig.
    *
    * @param file either an absolute or relative path, or a name on the classpath
    * @return a BenchConfig bean
    */
   public BenchConfig createConfig(String file) throws CannotLocateConfigException, ConfigParsingException {
      return (BenchConfig) parse(file);
   }

   private Object parse(String file) throws CannotLocateConfigException, ConfigParsingException {
      URL u = ConfigBuilder.findConfigFile(file);
      if (u == null) throw new CannotLocateConfigException(file);

      try {
         Unmarshaller unmarshaller = JAXBContext.newInstance("org.cachebench.fwk.config").createUnmarshaller();
         DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
         InputSource source;

         if (validating) {
            // get schema from classpath
            try {
               InputStream schema = getClass().getClassLoader().getResource("bench-config.xsd").openStream();
               SchemaFactory factory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
               unmarshaller.setSchema(factory.newSchema(new StreamSource(schema)));
               unmarshaller.setEventHandler(new ValidationEventHandler() {
                  @Override
                  public boolean handleEvent(ValidationEvent event) {
                     int severity = event.getSeverity();
                     return (severity != ValidationEvent.FATAL_ERROR && severity != ValidationEvent.ERROR);
                  }
               });
               dbf.setNamespaceAware(true);
            } catch (Exception e) {
               throw new ConfigParsingException("Unable to load XSD schema", e);
            }
         }

         source = replaceProperties(u.openStream());
         DocumentBuilder db = dbf.newDocumentBuilder();
         Document document = db.parse(source);
         Object object = unmarshaller.unmarshal(document);
         if (log.isTraceEnabled()) log.trace("unmarshalled XML file into " + object);
         return object;
      } catch (ConfigParsingException cpe) {
         throw cpe;
      } catch (Exception e) {
         throw new ConfigParsingException(e);
      }
   }

   private static InputSource replaceProperties(InputStream config) throws Exception {
      BufferedReader br = new BufferedReader(new InputStreamReader(config));
      StringBuilder w = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) {
         int dollar = line.indexOf('$');
         if (dollar > 0 && line.indexOf('{', dollar) > 0 && line.indexOf('}', dollar) > 0) {
            String replacedLine = replaceProperties(line);
            w.append(replacedLine);
         } else {
            w.append(line);
         }
      }
      return new InputSource(new StringReader(w.toString()));
   }

   public static String replaceProperties(final String string) {
      final char[] chars = string.toCharArray();
      StringBuffer buffer = new StringBuffer();
      boolean properties = false;
      int state = NORMAL;
      int start = 0;
      for (int i = 0; i < chars.length; ++i) {
         char c = chars[i];

         // Dollar sign outside brackets
         if (c == '$' && state != IN_BRACKET)
            state = SEEN_DOLLAR;

            // Open bracket immediatley after dollar
         else if (c == '{' && state == SEEN_DOLLAR) {
            buffer.append(string.substring(start, i - 1));
            state = IN_BRACKET;
            start = i - 1;
         }

         // No open bracket after dollar
         else if (state == SEEN_DOLLAR)
            state = NORMAL;

            // Closed bracket after open bracket
         else if (c == '}' && state == IN_BRACKET) {
            // No content
            if (start + 2 == i) {
               buffer.append("${}"); // REVIEW: Correct?
            } else // Collect the system property
            {
               String value = null;

               String key = string.substring(start + 2, i);

               // check for alias
               if (FILE_SEPARATOR_ALIAS.equals(key)) {
                  value = FILE_SEPARATOR;
               } else if (PATH_SEPARATOR_ALIAS.equals(key)) {
                  value = PATH_SEPARATOR;
               } else {
                  value = System.getProperty(key);

                  if (value == null) {
                     // Check for a default value ${key:default}
                     int colon = key.indexOf(':');
                     if (colon > 0) {
                        String realKey = key.substring(0, colon);
                        value = System.getProperty(realKey);

                        if (value == null) {
                           // Check for a composite key, "key1,key2"
                           value = resolveCompositeKey(realKey);

                           // Not a composite key either, use the specified default
                           if (value == null)
                              value = key.substring(colon + 1);
                        }
                     } else {
                        // No default, check for a composite key, "key1,key2"
                        value = resolveCompositeKey(key);
                     }
                  }
               }

               if (value != null) {
                  properties = true;
                  buffer.append(value);
               } else {
                  buffer.append("${");
                  buffer.append(key);
                  buffer.append('}');
               }

            }
            start = i + 1;
            state = NORMAL;
         }
      }

      // No properties
      if (!properties)
         return string;

      // Collect the trailing characters
      if (start != chars.length)
         buffer.append(string.substring(start, chars.length));

      // Done
      return buffer.toString();
   }

   private static String resolveCompositeKey(String key) {
      String value = null;

      // Look for the comma
      int comma = key.indexOf(',');
      if (comma > -1) {
         // If we have a first part, try resolve it
         if (comma > 0) {
            // Check the first part
            String key1 = key.substring(0, comma);
            value = System.getProperty(key1);
         }
         // Check the second part, if there is one and first lookup failed
         if (value == null && comma < key.length() - 1) {
            String key2 = key.substring(comma + 1);
            value = System.getProperty(key2);
         }
      }
      // Return whatever we've found or null
      return value;
   }
}

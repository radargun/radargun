package org.radargun.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.radargun.Service;
import org.radargun.ServiceHelper;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.ProvidesTrait;

/**
 * A service for starting Tomcat 8 server.
 *
 * The username and password properties must be set so the Tomcat
 * service can verify that the server is properly running.
 * The server is considered properly running once the query for
 * the following address returns "OK" in the response body:
 *    http://bindAddress:bindHttpPort/manager/text/list
 *
 * The conf/tomcat-users.xml file within Tomcat must include a user
 * with "manager-script" role and corresponding credentials.
 *
 * @author Martin Gencur
 */
@Service(doc = TomcatServerService.SERVICE_DESCRIPTION)
public class TomcatServerService extends JavaProcessService {

   protected final Log log = LogFactory.getLog(getClass());
   protected static final String SERVICE_DESCRIPTION = "Tomcat Server";
   private static final String MANAGER_CHARSET = "utf-8";

   @Property(doc = "Tomcat server home directory. Default value is commonly used environment variable CATALINA_HOME.")
   private String catalinaHome = System.getenv("CATALINA_HOME");

   @Property(doc = "Tomcat server base directory. Optional when only one instance of Tomcat is running on a single physical node. See http://tomcat.apache.org/tomcat-8.0-doc/RUNNING.txt for more details. Default value is commonly used environment variable CATALINA_BASE.")
   private String catalinaBase = System.getenv("CATALINA_BASE");

   @Property(doc = "Bind address. Default is localhost.")
   private String bindAddress = "localhost";

   @Property(doc = "HTTP port where the target Tomcat server is listening. Default is 8080.")
   private int bindHttpPort = 8080;

   @Property(doc = "Username to be used for managing the server. Empty by default.")
   private String user;

   @Property(doc = "Password to be used for managing the server. Empty by default.")
   private String pass;

   @Property(doc = "Remote JMX port. Default is 8089.")
   private int remotejmxPort = 8089;

   @Property(doc = "Logging properties file. Default is ${CATALINA_HOME}/conf/logging.properties")
   private String loggingProperties = "logging.properties";

   @ProvidesTrait
   public TomcatConfigurationProvider createTomcatConfigurationProvider() {
      return new TomcatConfigurationProvider(this);
   }

   @Init
   public void init() {
      if (catalinaBase == null || "".equals(catalinaBase))
         catalinaBase = catalinaHome;

      log.info("Java home: " + java);

      lifecycle = new TomcatServerLifecycle(this);
      try {
         URL resource = getClass().getResource("/" + file);
         Path filesystemFile = FileSystems.getDefault().getPath(file);
         Path target = FileSystems.getDefault().getPath(catalinaBase, "conf", "radargun-tomcat-" + ServiceHelper.getSlaveIndex() + ".xml");
         if (resource != null) {
            try (InputStream is = resource.openStream()) {
               log.info("Found " + file + " as a resource");
               Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            }
         } else if (filesystemFile.toFile().exists()) {
            log.info("Found " + file + " in plugin directory");
            Files.copy(filesystemFile, target, StandardCopyOption.REPLACE_EXISTING);
         } else if (FileSystems.getDefault().getPath(catalinaBase, "conf", file).toFile().exists()) {
            log.info("Found " + file + " in server conf/ directory");
            filesystemFile = FileSystems.getDefault().getPath(catalinaBase, "conf", file);
            // Set the file variable to the full path to the file to satisfy AbstractConfigurationProvider
            file = filesystemFile.toString();
            Files.copy(filesystemFile, target, StandardCopyOption.REPLACE_EXISTING);
         } else {
            throw new FileNotFoundException("File " + file + " not found neither as resource nor in filesystem.");
         }
      } catch (IOException e) {
         log.error("Failed to copy file", e);
         throw new RuntimeException(e);
      }
   }

   @Override
   protected List<String> getCommand() {
      final List<String> cmd = new ArrayList<String>();

      cmd.add(java + "/bin/java");
      cmd.add("-Djava.util.logging.config.file=" + catalinaBase + "/conf/" + loggingProperties);
      cmd.add("-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager");
      cmd.add("-Dcom.sun.management.jmxremote.port=" + remotejmxPort);
      cmd.add("-Dcom.sun.management.jmxremote.ssl=false");
      cmd.add("-Dcom.sun.management.jmxremote.authenticate=false");
      cmd.addAll(args);

      String classpath = catalinaHome + "/bin/bootstrap.jar" + File.pathSeparator +
         catalinaHome + "/bin/tomcat-juli.jar";

      cmd.add("-classpath");
      cmd.add(classpath);
      cmd.add("-Djava.endorsed.dirs=" + catalinaHome + "/endorsed");
      cmd.add("-Dcatalina.base=" + catalinaBase);
      cmd.add("-Dcatalina.home=" + catalinaHome);
      cmd.add("-Djava.io.tmpdir=" + catalinaBase + "/temp");
      cmd.add("org.apache.catalina.startup.Bootstrap");
      cmd.add("-config");
      cmd.add(catalinaBase + "/conf/radargun-tomcat-" + ServiceHelper.getSlaveIndex() + ".xml");
      cmd.add("start");

      return cmd;
   }

   boolean isTomcatReady() {
      try {
         queryTomcat("/text/list");
         return true;
      } catch (final IOException e) {
         return false;
      }
   }

   /**
    * Copied from Arquillian and modified.
    *
    * Execute the specified command, based on the configured properties. The input stream will be closed upon completion of
    * this task, whether it was executed successfully or not.
    *
    * @param command Command to be executed
    * @throws IOException
    */
   private void queryTomcat(final String command) throws IOException {
      URLConnection conn = new URL(getManagerUrl() + command).openConnection();
      final HttpURLConnection hconn = (HttpURLConnection) conn;

      // Set up standard connection characteristics
      hconn.setAllowUserInteraction(false);
      hconn.setDoInput(true);
      hconn.setUseCaches(false);
      hconn.setDoOutput(false);
      hconn.setRequestMethod("GET");

      if (user != null && user.length() != 0) {
         hconn.setRequestProperty("Authorization", constructHttpBasicAuthHeader());
      }
      hconn.setRequestProperty("Accept", "text/plain");

      // Establish the connection with the server
      hconn.connect();

      // Send the request data (if any)
      processResponse(command, hconn);
   }

   /**
    * Copied from Arquillian and modified.
    */
   private void processResponse(final String command, final HttpURLConnection hconn) throws IOException {
      final int httpResponseCode = hconn.getResponseCode();
      if (httpResponseCode >= 400 && httpResponseCode < 500) {
         throw new IllegalStateException(
            "Unable to connect to Tomcat manager. "
               + "The server command (" + command + ") failed with responseCode ("
               + httpResponseCode + ") and responseMessage (" + hconn.getResponseMessage()+ ").\n\n"
               + "Please make sure that you provided correct credentials for a user which is allowed to access Tomcat manager application.\n"
               + "The user must have 'manager-script' role specified in tomcat-users.xml file.\n");
      } else if (httpResponseCode >= 300) {
         throw new IllegalStateException("The server command (" + command + ") failed with responseCode ("
            + httpResponseCode + ") and responseMessage (" + hconn.getResponseMessage() + ").");
      }
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(hconn.getInputStream(), MANAGER_CHARSET))) {
         String line = reader.readLine();
         String contentError = null;
         if (line != null && !line.startsWith("OK -")) {
            contentError = line;
         }
         while (line != null) {
            line = reader.readLine();
         }
         if (contentError != null) {
            throw new IllegalStateException("The server command (" + command + ") failed with content (" + contentError + ").");
         }
      }
   }

   private String constructHttpBasicAuthHeader() {
      // Set up an authorization header with our credentials
      final String credentials = user + ":" + pass;
      // Encodes the user:password pair as a sequence of ISO-8859-1 bytes.
      try {
         return "Basic " + new String(Base64.getEncoder().encode(credentials.getBytes("ISO-8859-1")));
      } catch (final UnsupportedEncodingException e) {
         throw new RuntimeException(e);
      }
   }

   private URL getManagerUrl() {
      try {
         final String template = "http://%s:%d/manager";
         final String urlString = String.format(template, bindAddress, bindHttpPort);
         return new URL(urlString);
      } catch (MalformedURLException e) {
         throw new IllegalStateException("Manager URL is not valid", e);
      }
   }
}

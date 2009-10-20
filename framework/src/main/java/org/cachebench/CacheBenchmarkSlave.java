package org.cachebench;

import org.apache.commons.digester.Digester;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.config.Configuration;
import org.cachebench.config.TestCase;
import org.cachebench.utils.Instantiator;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Semaphore;

import sun.misc.Unsafe;

/**
 * @author Manik Surtani (manik@surtani.org)
 * @version $Id: CacheBenchmarkSlave.java,v 1.8 2007/05/21 16:30:00 msurtani Exp $
 * @deprecated all tests should run in server mode, as all the test are putting data into the test
 */
public class CacheBenchmarkSlave
{

   private Configuration conf;
   private Log logger = LogFactory.getLog("org.cachebench.CacheBenchmarkRunner");
   private Log errorLogger = LogFactory.getLog("CacheException");

   public static void main(String[] args)
   {
      String conf = null;
      if (args.length == 1)
      {
         conf = args[0];
      }
      if (conf != null && conf.toLowerCase().endsWith(".xml"))
      {
         new CacheBenchmarkSlave(conf);
      }
      else
      {
         new CacheBenchmarkSlave();
      }
   }

   private CacheBenchmarkSlave()
   {
      this("cachebench.xml");
   }

   private CacheBenchmarkSlave(String s)
   {
      // first, try and find the configuration on the filesystem.
      URL confFile = findOnFS(s);
      final Object o = new Object();
      if (confFile == null)
      {
         confFile = findInClasspath(s);
      }
      if (confFile == null)
      {
         logger.warn("Unable to locate a configuration file; Application terminated");
      }
      else
      {
         if (logger.isDebugEnabled()) logger.debug("Using configuration " + confFile);
         logger.debug("Parsing configuration");
         try
         {
            conf = parseConfiguration(confFile);
            logger.info("Starting Slave....");

            // will only start the first valid test.  Slaves don't support more than one test at a time
            List<TestCase> cases = conf.getTestCases();
            if (cases.size() == 0) throw new RuntimeException("Unable to proceed; no tests configured!");
            if (cases.size() != 1)
               logger.warn("Slaves only support running one test case at a time.  You have " + cases.size() + " cases configured.  Will only attempt the first one.");

            CacheWrapper c = getCacheWrapperInstance(cases.get(0));
            c.init(cases.get(0).getParams());
            c.setUp();

            logger.info("Slave is listening.  CTRL-C to kill.");
//            Semaphore sema = new Semaphore(1);

            // hack to cause the main thread to wait forever.
//            for (int i = 0; i<2; i++) sema.acquire();
            boolean debug = Boolean.getBoolean("org.cachebench.debug");
            if (debug) System.out.println("DEBUG mode is true.  Will dump cache contents periodically");
            System.out.println("Slave listening... ");
            while (true)
            {
               Thread.sleep(debug ? 5000 : 240000);
               System.out.println(c.getInfo());
            }
         }
         catch (Exception e)
         {
            logger.warn("Unable to parse configuration file " + confFile + ". Application terminated.", e);
            errorLogger.fatal("Unable to parse configuration file " + confFile, e);
         }
      }
   }

   private Configuration parseConfiguration(URL url) throws Exception
   {
      Digester digester = new Digester();
      // set up the digester rules.
      digester.setValidating(false);
      digester.addObjectCreate("cachebench", "org.cachebench.config.Configuration");
      digester.addSetProperties("cachebench");
      digester.addObjectCreate("cachebench/testcase", "org.cachebench.config.TestCase");
      digester.addSetProperties("cachebench/testcase");

      digester.addObjectCreate("cachebench/testcase/test", "org.cachebench.config.TestConfig");
      digester.addSetProperties("cachebench/testcase/test");
      digester.addSetNext("cachebench/testcase/test", "addTest", "org.cachebench.config.TestConfig");

      digester.addObjectCreate("cachebench/testcase/param", "org.cachebench.config.NVPair");
      digester.addSetProperties("cachebench/testcase/param");

      digester.addSetNext("cachebench/testcase/param", "addParam", "org.cachebench.config.NVPair");
      digester.addSetNext("cachebench/testcase", "addTestCase", "org.cachebench.config.TestCase");

      digester.addObjectCreate("cachebench/report", "org.cachebench.config.Report");
      digester.addSetProperties("cachebench/report");
      digester.addSetNext("cachebench/report", "addReport", "org.cachebench.config.Report");
      return (Configuration) digester.parse(url.openStream());
   }

   /**
    * Util method to locate a resource on the filesystem as a URL
    *
    * @param filename
    * @return The URL object of the file
    */
   private URL findOnFS(String filename)
   {
      File f = new File(filename);
      try
      {
         if (f.exists()) return f.toURL();
      }
      catch (MalformedURLException mue)
      {
         // bad URL
      }
      return null;
   }

   /**
    * Util method to locate a resource in your classpath
    *
    * @param filename
    * @return The URL object of the file
    */
   private URL findInClasspath(String filename)
   {
      return getClass().getClassLoader().getResource(filename);
   }

   private CacheWrapper getCacheWrapperInstance(TestCase testCaseClass)
   {
      CacheWrapper cache = null;
      try
      {
         cache = (CacheWrapper) Instantiator.getInstance().createClass(testCaseClass.getCacheWrapper());

      }
      catch (Exception e)
      {
         logger.warn("Unable to instantiate CacheWrapper class: " + testCaseClass.getCacheWrapper() + " - Not Running any tests");
         errorLogger.error("Unable to instantiate CacheWrapper class: " + testCaseClass.getCacheWrapper(), e);
         errorLogger.error("Skipping this test");
      }
      return cache;
   }
}

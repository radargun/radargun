package org.cachebench.config;

import org.apache.commons.digester.Digester;

import java.net.URL;
import java.net.MalformedURLException;
import java.io.File;

/**
 * Helper class for loading configurations.
 *
 * @author Mircea.Markus@jboss.com
 */
public class ConfigBuilder
{
   /**
    * Util method to locate a resource on the filesystem as a URL
    *
    * @param filename
    * @return The URL object of the file
    */
   private static URL findOnFS(String filename)
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
    * Looks for config file on disk then on class path.
    * @return null if the file cannot be found
    */
   public static URL findConfigFile(String s)
   {
      URL confFile = findOnFS(s);
      if (confFile == null)
      {
         confFile = findInClasspath(s);
      }
      return confFile;
   }

   public static Configuration parseConfiguration(URL url) throws Exception
   {
      Digester digester = new Digester();
      // set up the digester rules.
      digester.setValidating(false);
      digester.addObjectCreate("cachebench", "org.cachebench.config.Configuration");
      digester.addSetProperties("cachebench");

      digester.addObjectCreate("cachebench/cluster", "org.cachebench.config.ClusterConfig");
      digester.addSetProperties("cachebench/cluster");
      digester.addObjectCreate("cachebench/cluster/member", "org.cachebench.config.NodeAddress");
      digester.addSetProperties("cachebench/cluster/member");
      digester.addSetNext("cachebench/cluster/member", "addMember", "org.cachebench.config.NodeAddress");
      digester.addSetNext("cachebench/cluster", "setClusterConfig", "org.cachebench.config.ClusterConfig");

      digester.addObjectCreate("cachebench/testcase", "org.cachebench.config.TestCase");
      digester.addSetProperties("cachebench/testcase");

      digester.addObjectCreate("cachebench/testcase/warmup", "org.cachebench.config.CacheWarmupConfig");
      digester.addSetProperties("cachebench/testcase/warmup");

      digester.addObjectCreate("cachebench/testcase/warmup/param", "org.cachebench.config.NVPair");
      digester.addSetProperties("cachebench/testcase/warmup/param");
      digester.addSetNext("cachebench/testcase/warmup/param", "addParam", "org.cachebench.config.NVPair");

      digester.addSetNext("cachebench/testcase/warmup", "setCacheWarmupConfig", "org.cachebench.config.CacheWarmupConfig");

      digester.addObjectCreate("cachebench/testcase/test", "org.cachebench.config.TestConfig");
      digester.addSetProperties("cachebench/testcase/test");
      digester.addObjectCreate("cachebench/testcase/test/param", "org.cachebench.config.NVPair");
      digester.addSetProperties("cachebench/testcase/test/param");
      digester.addSetNext("cachebench/testcase/test/param", "addParam", "org.cachebench.config.NVPair");
      digester.addSetNext("cachebench/testcase/test", "addTest", "org.cachebench.config.TestConfig");

      digester.addObjectCreate("cachebench/testcase/param", "org.cachebench.config.NVPair");
      digester.addSetProperties("cachebench/testcase/param");

      digester.addSetNext("cachebench/testcase/param", "addParam", "org.cachebench.config.NVPair");
      digester.addSetNext("cachebench/testcase", "addTestCase", "org.cachebench.config.TestCase");

      digester.addObjectCreate("cachebench/report", "org.cachebench.config.Report");
      digester.addSetProperties("cachebench/report");

      digester.addObjectCreate("cachebench/report/param", "org.cachebench.config.NVPair");
      digester.addSetProperties("cachebench/report/param");
      digester.addSetNext("cachebench/report/param", "addParam", "org.cachebench.config.NVPair");

      digester.addSetNext("cachebench/report", "addReport", "org.cachebench.config.Report");
      return (Configuration) digester.parse(url.openStream());
   }

   /**
     * Util method to locate a resource in your classpath
     */
    private static URL findInClasspath(String filename)
    {
       return ConfigBuilder.class.getClassLoader().getResource(filename);
    }


}

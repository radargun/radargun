package org.cachebench.config;

import org.cachebench.plugins.PluginLocator;

import java.util.ArrayList;
import java.util.List;


public class TestCase extends GenericParamsConfig
{
   private String name;
   private String cacheWrapper;

   private boolean stopOnFailure = true;

   private List<TestConfig> tests = new ArrayList<TestConfig>();

   private CacheWarmupConfig cacheWarmupConfig;


   public CacheWarmupConfig getCacheWarmupConfig()
   {
      return cacheWarmupConfig;
   }

   public void setCacheWarmupConfig(CacheWarmupConfig cacheWarmupConfig)
   {
      this.cacheWarmupConfig = cacheWarmupConfig;
   }

   public String getName()
   {
      return name;
   }

   public void setName(String name)
   {
      this.name = name;
   }

   /**
    * @return Returns the cacheWrapper.
    */
   public String getCacheWrapper()
   {
      if (cacheWrapper == null)
      {
         cacheWrapper = PluginLocator.locatePlugin();
      }
      if (cacheWrapper == null)
      {
         throw new IllegalStateException("Null cache wrapper, it was not specified in neither in 'cachebench.xml' " +
               " nor as sys prop 'cacheBenchFwk.cacheWrapperClassName'");
      }
      return cacheWrapper;
   }

   /**
    * @param cacheWrapper The cacheWrapper to set.
    */
   public void setCacheWrapper(String cacheWrapper)
   {
      this.cacheWrapper = cacheWrapper;
   }

   public List<TestConfig> getTests()
   {
      return this.tests;
   }

   public void addTest(TestConfig test)
   {
      tests.add(test);
   }

   public boolean isStopOnFailure()
   {
      return stopOnFailure;
   }

   public void setStopOnFailure(boolean stopOnFailure)
   {
      this.stopOnFailure = stopOnFailure;
   }

   public TestConfig getTest(String testName)
   {
      for (TestConfig config : getTests())
      {
         if (testName.equals(config.getName()))
         {
            return config;
         }
      }
      return null;
   }
}
package org.cachebench.config;


public class TestConfig extends GenericParamsConfig
{
   private String name;
   private String testClass;
   private float weight;
   private int repeat = 1;


   /**
    * @return Returns the name.
    */
   public String getName()
   {
      return name;
   }

   /**
    * @param name The name to set.
    */
   public void setName(String name)
   {
      this.name = name;
   }

   /**
    * @return Returns the testClass.
    */
   public String getTestClass()
   {
      return testClass;
   }

   /**
    * @param testClass The testClass to set.
    */
   public void setTestClass(String testClass)
   {
      this.testClass = testClass;
   }

   /**
    * @return Returns the weight.
    */
   public float getWeight()
   {
      return weight;
   }

   /**
    * @param weight The weight to set.
    */
   public void setWeight(float weight)
   {
      this.weight = weight;
   }

   public int getRepeat()
   {
      return repeat;
   }

   public void setRepeat(int repeat)
   {
      this.repeat = repeat;
   }
}
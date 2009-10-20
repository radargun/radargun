package org.cachebench.config;

public class Report extends GenericParamsConfig
{
   private String generator;
   private String outputFile;

   public String getGenerator()
   {
      return generator;
   }

   public void setGenerator(String generator)
   {
      this.generator = generator;
   }

   public String getOutputFile()
   {
      return outputFile;
   }

   public void setOutputFile(String outputFile)
   {
      this.outputFile = outputFile;
   }

}
package org.cachebench.config;


/**
 * @author Manik Surtani (manik@surtani.org)
 * @version $Id: NVPair.java,v 1.2 2007/03/13 14:50:46 msurtani Exp $
 */
public class NVPair
{
   private String name;
   private String value;

   public String getName()
   {
      return name;
   }

   public void setName(String name)
   {
      this.name = name;
   }

   public String getValue()
   {
      return value;
   }

   public void setValue(String value)
   {
      this.value = value;
   }
}

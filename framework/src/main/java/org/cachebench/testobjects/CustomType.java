package org.cachebench.testobjects;

import java.util.Date;


/**
 * @author Manik Surtani (manik@surtani.org)
 * @version $Id: CustomType.java,v 1.3 2007/03/13 14:50:45 msurtani Exp $
 */
public class CustomType
{
   protected String subString;
   protected int subInt;
   protected float subFloat;
   protected long subLong;
   protected Date subDate = new Date();

   public CustomType() {
   }

   public CustomType(int seed) {
      subInt = seed;
      subFloat = seed;
      subLong = seed;
      subDate.setTime(System.currentTimeMillis() + seed * 100);
      subString = getClass().getSimpleName() + "-" + seed;
   }
}

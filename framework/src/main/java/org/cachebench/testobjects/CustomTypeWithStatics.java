package org.cachebench.testobjects;

import java.util.Date;


/**
 * @author Manik Surtani (manik@surtani.org)
 * @version $Id: CustomTypeWithStatics.java,v 1.2 2007/03/13 14:50:45 msurtani Exp $
 */
public class CustomTypeWithStatics {
   protected String subString = "subclass string";
   protected int subInt = 5;
   protected float subFloat = 5.0f;
   protected long subLong = 5l;
   protected Date subDate = new Date();

   protected static String stString = "static string";
   protected static Date stDate = new Date();

   public CustomTypeWithStatics() {
   }

   public CustomTypeWithStatics(int seed) {
      subInt = seed;
      subFloat = seed;
      subLong = seed;
      subDate.setTime(System.currentTimeMillis() + seed * 100);
      subString = getClass().getSimpleName() + "-" + seed;
   }
}

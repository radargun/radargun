package org.cachebench.testobjects;

import java.util.Date;


/**
 * @author Manik Surtani (manik@surtani.org)
 * @version $Id: CustomTypeWithAssocs.java,v 1.2 2007/03/13 14:50:45 msurtani Exp $
 */
public class CustomTypeWithAssocs
{
   protected String subString;
   protected int subInt;
   protected float subFloat;
   protected long subLong;
   protected Date subDate = new Date();

   // recursive assoc
   protected CustomType assoc1 = new CustomType();
   protected CustomType assoc2 = new CustomType();
   protected CustomType assoc3 = new CustomType();

   public CustomTypeWithAssocs() {
   }

   public CustomTypeWithAssocs(int seed) {
      subInt = seed;
      subFloat = seed;
      subLong = seed;
      subDate.setTime(System.currentTimeMillis() + seed * 100);
      subString = getClass().getSimpleName() + "-" + seed;
   }
}


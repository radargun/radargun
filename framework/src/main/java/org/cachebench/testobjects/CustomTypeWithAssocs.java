package org.cachebench.testobjects;

import java.util.Date;


/**
 * @author Manik Surtani (manik@surtani.org)
 * @version $Id: CustomTypeWithAssocs.java,v 1.2 2007/03/13 14:50:45 msurtani Exp $
 */
public class CustomTypeWithAssocs
{
   protected String subString = "subclass string";
   protected int subInt = 5;
   protected float subFloat = 5.0f;
   protected long subLong = 5l;
   protected Date subDate = new Date();

   // recursive assoc
   protected CustomType assoc1 = new CustomType();
   protected CustomType assoc2 = new CustomType();
   protected CustomType assoc3 = new CustomType();
}


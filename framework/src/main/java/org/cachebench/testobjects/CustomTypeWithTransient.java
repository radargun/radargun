package org.cachebench.testobjects;

import java.util.Date;


/**
 * @author Manik Surtani (manik@surtani.org)
 * @version $Id: CustomTypeWithTransient.java,v 1.3 2007/03/13 14:50:45 msurtani Exp $
 */
public class CustomTypeWithTransient
{
   protected String subString = "subclass string";
   protected int subInt = 5;
   protected float subFloat = 5.0f;
   protected long subLong = 5l;
   protected Date subDate = new Date();

   protected transient String trString = "transient";
   protected transient Date trDate = new Date();
}

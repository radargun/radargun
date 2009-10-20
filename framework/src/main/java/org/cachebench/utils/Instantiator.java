package org.cachebench.utils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * @author Manik Surtani (manik@surtani.org)
 * @version $Id: Instantiator.java,v 1.4 2007/03/13 14:50:46 msurtani Exp $
 */
public class Instantiator
{
   private static Instantiator _singleton;
   private Log logger = LogFactory.getLog("org.cachebench.utils.Instantiator");

   private Instantiator()
   {
   }

   public static Instantiator getInstance()
   {
      if (_singleton == null) _singleton = new Instantiator();
      return _singleton;
   }

   public Object createClass(String className) throws Exception
   {
      Class c = getClass().getClassLoader().loadClass(className);
      return c.newInstance();
   }
}

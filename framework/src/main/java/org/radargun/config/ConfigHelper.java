package org.radargun.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Element;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

/**
 * Contains various configuration helper methods needed by different parsers.
 *
 * @author Mircea.Markus@jboss.com
 */
public class ConfigHelper {

   private static Log log = LogFactory.getLog(ConfigHelper.class);

   /**
    * Retrieves a setter name based on a field name passed in
    *
    * @param fieldName field name to find setter for
    * @return name of setter method
    */
   public static String setterName(String fieldName) {
      StringBuilder sb = new StringBuilder("set");
      if (fieldName != null && fieldName.length() > 0) {
         sb.append(fieldName.substring(0, 1).toUpperCase(Locale.ENGLISH));
         if (fieldName.length() > 1) {
            sb.append(fieldName.substring(1));
         }
      }
      return sb.toString();
   }

   public static void setValues(Object target, Map<?, ?> attribs, boolean failOnMissingSetter) {
      Class objectClass = target.getClass();

      // go thru simple string setters first.
      for (Map.Entry entry : attribs.entrySet()) {
         String propName = (String) entry.getKey();
         String setter = setterName(propName);
         Method method;

         try {
            method = objectClass.getMethod(setter, String.class);
            method.invoke(target, entry.getValue());
            continue;
         }
         catch (NoSuchMethodException me) {
            // try other setters that may fit later on.  Don't throw this exception though.
         }
         catch (Exception e) {
            throw new RuntimeException("Unable to invoke setter " + setter + " on " + objectClass, e);
         }

         boolean setterFound = false;
         // if we get here, we could not find a String or Element setter.
         for (Method m : objectClass.getMethods()) {
            if (setter.equals(m.getName())) {
               Class paramTypes[] = m.getParameterTypes();
               if (paramTypes.length != 1) {
                  if (log.isTraceEnabled()) {
                     log.trace("Rejecting setter " + m + " on class " + objectClass + " due to incorrect number of parameters");
                  }
                  continue; // try another param with the same name.
               }

               Class parameterType = paramTypes[0];
               PropertyEditor editor = PropertyEditorManager.findEditor(parameterType);
               if (editor == null) {
                  throw new RuntimeException("Couldn't find a property editor for parameter type " + parameterType);
               }

               editor.setAsText((String) attribs.get(propName));

               Object parameter = editor.getValue();
               //if (log.isDebugEnabled()) log.debug("Invoking setter method: " + setter + " with parameter \"" + parameter + "\" of type " + parameter.getClass());

               try {
                  m.invoke(target, parameter);
                  setterFound = true;
                  break;
               }
               catch (Exception e) {
                  throw new RuntimeException("Unable to invoke setter " + setter + " on " + objectClass, e);
               }
            }
         }
         if (!setterFound && failOnMissingSetter) {
            throw new RuntimeException("Couldn't find a setter named [" + setter + "] which takes a single parameter, for parameter " + propName + " on class [" + objectClass + "]");
         }
      }
   }

   //looks for this syntax: ${defaultValue:existingPropValue}
   //this is also supporrted: ${existingPropValue}
   public static String checkForProps(String val) {
      if (val == null) return val;
      val = val.trim();
      if (val.length() <= "${}".length())
         return val;
      String originalVal = val;
      if (val.indexOf("${") == 0) {
         //get rid of '${' and '}'
         val = val.substring(2, val.length() - 1);
         int separator = val.indexOf(':');
         if (separator > 0) {
            String defaultValue = val.substring(0, separator);
            String sysProperty = val.substring(separator + 1);
            String inEnv = System.getProperties().getProperty(sysProperty);
            if (inEnv != null) {
               return inEnv;
            } else {
               return defaultValue;
            }
         } else {
            String sysProp = System.getProperties().getProperty(val);
            if (sysProp == null) {
               String errorMessage = "For property '" + originalVal + "' there's no System.property with key " + val
                     + " .Existing properties are: " + System.getProperties();
               log.error(errorMessage);
               throw new RuntimeException(errorMessage);
            } else {
               return sysProp;
            }
         }
      } else {
         return val;
      }
   }

   public static int parseInt(String val) {
      val = checkForProps(val);
      return Integer.valueOf(val);
   }

   public static float parseFloat(String val) {
      val = checkForProps(val);
      return Float.valueOf(val);
   }

   public static String parseString(String value) {
      return checkForProps(value);
   }

   public static boolean parseBoolean(String value) {
      return Boolean.valueOf(checkForProps(value));
   }

   public static String getStrAttribute(Element master, String attrName) {
      String s = master.getAttribute(attrName);
      return parseString(s);
   }

   public static int getIntAttribute(Element master, String attrName) {
      String s = master.getAttribute(attrName);
      return Integer.parseInt(parseString(s));
   }
}

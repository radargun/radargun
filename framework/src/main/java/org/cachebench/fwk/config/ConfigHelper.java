package org.cachebench.fwk.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.fwk.BenchmarkServer;
import org.cachebench.fwk.ServerConfig;
import org.w3c.dom.Element;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * // TODO: Mircea - Document this!
 *
 * @author Mircea.Markus@jboss.com
 */
public class ConfigHelper {

   private static Log log = LogFactory.getLog(ConfigHelper.class);

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


   public static void setValues(Object target, Map<?, ?> attribs, boolean isXmlAttribs, boolean failOnMissingSetter) {
      Class objectClass = target.getClass();

      // go thru simple string setters first.
      for (Map.Entry entry : attribs.entrySet()) {
         String propName = (String) entry.getKey();
         String setter = setterName(propName);
         Method method;

         try {
            if (isXmlAttribs) {
               method = objectClass.getMethod(setter, Element.class);
               method.invoke(target, entry.getValue());
            } else {
               method = objectClass.getMethod(setter, String.class);
               method.invoke(target, entry.getValue());
            }

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


   public static BenchmarkServer getServer(BenchConfig benchConfig) {
      Server server = benchConfig.getServer();
      ServerConfig serverConfig = new ServerConfig(server.getPort(), server.getBind(), server.getNodes());
      BenchmarkServer result = new BenchmarkServer(serverConfig);
      for (Stage stage : benchConfig.getStages().getStage()) {
         List<Property> list = stage.getProperty();
         org.cachebench.fwk.Stage st = getStage(stage.getName());
         Map<String, String> simpleProps = new HashMap<String, String>();
         Map<String, Map> aggregatedProps = new HashMap<String, Map>();
         for (Property prop : list) {
            if (prop.getMapAggregator() == null) {
               simpleProps.put(prop.getName(), prop.getValue());
            } else {
               Map aggregator = aggregatedProps.get(prop.getMapAggregator());
               if (aggregator == null) {
                  aggregator = new HashMap();
                  aggregatedProps.put(prop.getMapAggregator(), aggregator);
               }
               aggregator.put(prop.getName(), prop.getValue());
            }
         }
         setValues(st, simpleProps, false, true);
         setAggregatedValues(st, aggregatedProps);
         serverConfig.addStage(st);
      }
      return result;
   }

   private static void setAggregatedValues(org.cachebench.fwk.Stage st, Map<String, Map> aggregatedProps) {
      for (String propName : aggregatedProps.keySet()) {
         String setterName = "set" + propName.substring(0, 1).toUpperCase() + propName.substring(1);
         Method method;
         try {
            method = st.getClass().getMethod(setterName, Map.class);
         } catch (NoSuchMethodException e) {
            String errorMsg = "Could not find a setter '" + setterName + "' on class " + st.getClass();
            log.error(errorMsg);
            throw new RuntimeException(e);
         }
         try {
            method.invoke(st, aggregatedProps.get(propName));
         } catch (Throwable e) {
            log.error(e);
            throw new RuntimeException(e);
         }
      }
   }


   public static org.cachebench.fwk.Stage getStage(String stageName) {
      if (stageName.indexOf('.') < 0) {
         stageName = "org.cachebench.fwk.stages." + stageName;
      }
      try {
         return (org.cachebench.fwk.Stage) Class.forName(stageName).newInstance();
      } catch (Exception e) {
         String s = "Could not create stage of type: " + stageName;
         log.error(s);
         throw new RuntimeException(e);
      }
   }
}

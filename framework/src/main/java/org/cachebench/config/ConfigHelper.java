package org.cachebench.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.Master;
import org.cachebench.config.jaxb.BenchConfig;
import org.cachebench.config.jaxb.FixedSizeBenchmark;
import org.cachebench.config.jaxb.Property;
import org.cachebench.config.jaxb.ScalingBenchmark;
import org.cachebench.config.jaxb.Stage;
import org.cachebench.config.jaxb.Before;
import org.cachebench.config.jaxb.After;
import org.w3c.dom.Element;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Helper class for assembling JAXB configs. 
 *
 * @author Mircea.Markus@jboss.com
 * //TODO - add support for System.getEnv
 * //TODO - make sure that if a benchmark has more nodes than the root an exception is thrown  
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


   public static Master getMaster(BenchConfig benchConfig) {
      org.cachebench.config.jaxb.Master master = benchConfig.getMaster();
      int port = master.getPort() != null ? toInt(master.getPort()) : Master.DEFAULT_PORT;
      MasterConfig masterConfig = new MasterConfig(port, master.getBind(), toInt(master.getSlavesCount()));
      for (ScalingBenchmark sb : benchConfig.getScalingBenchmark()) {
         ScalingBenchmarkConfig sbc = new ScalingBenchmarkConfig();
         sbc.setName(sb.getName());
         sbc.setInitSize(toInt(sb.getInitSize()));
         sbc.setMaxSize(toInt(sb.getMaxSize()));
         sbc.setIncrement(toInt(sb.getIncrement()));
         Before before = sb.getBefore();
         //before and after are optional
         if (before != null) {
            List<Stage> beforeStagesFromXml = before.getStage();
            sbc.setBeforeStages(processStages(beforeStagesFromXml));
         }

         List<Stage> benchmarkStagesFromXml = sb.getBenchmarkStages().getStage();
         sbc.setStages(processStages(benchmarkStagesFromXml));

         After after = sb.getAfter();
         if (after != null) {
            List<Stage> afterStagesFromXml = after.getStage();
            sbc.setAfterStages(processStages(afterStagesFromXml));
         }

         sbc.validate();
         masterConfig.addBenchmark(sbc);
      }
      for (FixedSizeBenchmark fb : benchConfig.getFixedSizeBenchmark()) {
         FixedSizeBenchmarkConfig fbc = new FixedSizeBenchmarkConfig();
         fbc.setName(fb.getName());
         fbc.setSize(toInt(fb.getSize()));
         List<Stage> stagesFromXml = fb.getStage();
         fbc.setStages(processStages(stagesFromXml));
         fbc.validate();
         masterConfig.addBenchmark(fbc);
      }
      masterConfig.validate();
      return new Master(masterConfig);
   }

   private static List<org.cachebench.Stage> processStages(List<Stage> stagesFromXml) {
      List<org.cachebench.Stage> result = new ArrayList<org.cachebench.Stage>();
      for (Stage stage : stagesFromXml) {
         List<Property> list = stage.getProperty();
         org.cachebench.Stage st = getStage(stage.getName());
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
         result.add(st);
      }
      return result;
   }

   private static void setAggregatedValues(org.cachebench.Stage st, Map<String, Map> aggregatedProps) {
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


   public static org.cachebench.Stage getStage(String stageName) {
      if (stageName.indexOf('.') < 0) {
         stageName = "org.cachebench.stages." + stageName;
      }
      try {
         return (org.cachebench.Stage) Class.forName(stageName).newInstance();
      } catch (Exception e) {
         String s = "Could not create stage of type: " + stageName;
         log.error(s);
         throw new RuntimeException(e);
      }
   }

   private static int toInt(String str) {
      return Integer.parseInt(str);
   }
}

package org.radargun.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.radargun.Properties;
import org.radargun.stages.ScenarioCleanupStage;
import org.radargun.stages.ScenarioInitStage;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Mircea.Markus@jboss.com
 */
public class DomConfigParser extends ConfigParser implements ConfigSchema {

   String PROPERTY_PREFIX_REPEAT = "repeat.";
   String PROPERTY_SUFFIX_COUNTER = "counter";

   public MasterConfig parseConfig(String config) throws Exception {
      //the content in the new file is too dynamic, let's just use DOM for now

      DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

      Document document;
      try {
         document = builder.parse(config);
      }
      catch (Exception e) {
         throw new IllegalStateException(e);
      }

      Element root = (Element) document.getElementsByTagName(ELEMENT_BENCHMARK).item(0);
      NodeList childNodes = root.getChildNodes();
      int index = 0;
      index = nextElement(childNodes, index);
      MasterConfig masterConfig = parseMaster((Element) childNodes.item(index));
      index = nextElement(childNodes, index + 1);
      parseClusters(masterConfig, (Element) childNodes.item(index));
      index = nextElement(childNodes, index + 1);
      parseConfigurations(masterConfig, (Element) childNodes.item(index));
      index = nextElement(childNodes, index + 1);

      Scenario scenario = new Scenario();
      Map<String, String> initProperties = Collections.EMPTY_MAP;
      if (ELEMENT_INIT.equals(childNodes.item(index).getNodeName())) {
         initProperties = parseProperties(((Element) childNodes.item(index)));
         index = nextElement(childNodes, index + 1);
      }
      scenario.addStage(ScenarioInitStage.class, initProperties, Collections.EMPTY_MAP);

      parseScenario(scenario, (Element) childNodes.item(index));
      masterConfig.setScenario(scenario);

      index = nextElement(childNodes, index + 1);
      Map<String, String> cleanupProperties = Collections.EMPTY_MAP;
      if (ELEMENT_CLEANUP.equals(childNodes.item(index).getNodeName())) {
         cleanupProperties = parseProperties((Element) childNodes.item(index));
         index = nextElement(childNodes, index + 1);
      }
      scenario.addStage(ScenarioCleanupStage.class, cleanupProperties, Collections.EMPTY_MAP);
      parseReporting(masterConfig, (Element) childNodes.item(index));

      return masterConfig;
   }

   private int nextElement(NodeList nodeList, int start) {
      for (int i = start; i < nodeList.getLength(); ++i) {
         if (nodeList.item(i) instanceof Element) {
            return i;
         }
      }
      return -1;
   }

   private void assertName(String name, Element element) {
      if (!name.equals(element.getNodeName())) {
         throw new IllegalArgumentException("Found '" + element.getNodeName() + "', expected '"  + name  + "'");
      }
   }

   private String getAttribute(Element element, String attribute) {
      String value = Evaluator.parseString(element.getAttribute(attribute));
      if (value == null || value.isEmpty()) {
         throw new IllegalArgumentException("Element '" + attribute + "' must be defined.");
      }
      return value;
   }

   private String getAttribute(Element element, String attribute, String def) {
      String value = Evaluator.parseString(element.getAttribute(attribute));
      if (value == null || value.isEmpty()) {
         return def;
      }
      return value;
   }

   private MasterConfig parseMaster(Element masterElement) {
      assertName(ELEMENT_MASTER, masterElement);
      String bindAddress = getAttribute(masterElement, ATTR_BIND_ADDRESS, "localhost");
      String portString = getAttribute(masterElement, ATTR_PORT, "-1");
      return new MasterConfig(Integer.parseInt(portString), bindAddress);
   }

   private void parseClusters(MasterConfig masterConfig, Element clustersElement) {
      if (ELEMENT_LOCAL.equals(clustersElement.getNodeName())) {
         // no clusters, leave empty
         return;
      } else if (!ELEMENT_CLUSTERS.equals(clustersElement.getNodeName())) {
         throwExpected(clustersElement.getNodeName(), new String[] { ELEMENT_LOCAL, ELEMENT_CLUSTERS });
      }
      String clusterSizeBackup = System.getProperty(Properties.PROPERTY_CLUSTER_SIZE);
      NodeList clusters = clustersElement.getChildNodes();
      for (int i = 0; i < clusters.getLength(); ++i) {
         if (!(clusters.item(i) instanceof Element)) continue;
         Element childElement = (Element) clusters.item(i);
         if (ELEMENT_CLUSTER.equals(childElement.getNodeName())) {
            int size = Integer.parseInt(getAttribute(childElement, ATTR_SIZE, "0"));
            System.setProperty(Properties.PROPERTY_CLUSTER_SIZE, String.valueOf(size));
            addCluster(masterConfig, childElement, size);
         } else if (ELEMENT_SCALE.equals(childElement.getNodeName())) {
            int initSize = Integer.parseInt(getAttribute(childElement, ATTR_FROM));
            int maxSize = Integer.parseInt(getAttribute(childElement, ATTR_TO));
            int increment = Integer.parseInt(getAttribute(childElement, ATTR_INC, "1"));
            if (increment <= 0) throw new IllegalArgumentException("Increment must be > 0!");
            NodeList scaledElements = childElement.getChildNodes();
            for (int size = initSize; size <= maxSize; size += increment) {
               System.setProperty(Properties.PROPERTY_CLUSTER_SIZE, String.valueOf(size));
               for (int j = 0; j < scaledElements.getLength(); ++j) {
                  if (!(scaledElements.item(j) instanceof Element)) continue;
                  Element clusterElement = (Element) scaledElements.item(j);
                  assertName(ELEMENT_CLUSTER, clusterElement);
                  addCluster(masterConfig, clusterElement, size);
               }
            }
         } else {
            throwExpected(childElement.getNodeName(), new String[] { ELEMENT_CLUSTER, ELEMENT_SCALE} );
         }
         System.setProperty(Properties.PROPERTY_CLUSTER_SIZE, "");
      }
      if (clusterSizeBackup != null)
         System.setProperty(Properties.PROPERTY_CLUSTER_SIZE, clusterSizeBackup);
   }

   private void addCluster(MasterConfig masterConfig, Element clusterElement, int size) {
      Cluster cluster = new Cluster();
      NodeList groups = clusterElement.getChildNodes();
      for (int j = 0; j < groups.getLength(); ++j) {
         if (!(groups.item(j) instanceof Element)) continue;
         Element groupElement = (Element) groups.item(j);
         assertName(ELEMENT_GROUP, groupElement);
         String name = getAttribute(groupElement, ATTR_NAME);
         String groupSize = getAttribute(groupElement, ATTR_SIZE);
         cluster.addGroup(name, Integer.parseInt(groupSize));
      }
      if (size > 0) {
         if (cluster.getSize() <= 0) {
            cluster.setSize(size);
         } else if (cluster.getSize() != size) {
            throw new IllegalArgumentException("Total size for cluster is not the one specified as size! " + cluster);
         }
      }
      masterConfig.addCluster(cluster);
   }

   private void parseConfigurations(MasterConfig masterConfig, Element configsElement) {
      assertName(ELEMENT_CONFIGURATIONS, configsElement);
      NodeList configs = configsElement.getChildNodes();
      for (int i = 0; i < configs.getLength(); ++i) {
         if (!(configs.item(i) instanceof Element)) continue;
         Element configElement = (Element) configs.item(i);
         assertName(ELEMENT_CONFIG, configElement);
         String configName = getAttribute(configElement, ATTR_NAME);
         Configuration config = new Configuration(configName);
         NodeList setups = configElement.getChildNodes();
         for (int j = 0; j < setups.getLength(); ++j) {
            if (!(setups.item(j) instanceof Element)) continue;
            Element setupElement = (Element) setups.item(j);
            assertName(ELEMENT_SETUP, setupElement);
            String plugin = getAttribute(setupElement, ATTR_PLUGIN);
            String file = getAttribute(setupElement, ATTR_FILE);
            String service = getAttribute(setupElement, ATTR_SERVICE, Configuration.DEFAULT_SERVICE);
            String group = getAttribute(setupElement, ATTR_GROUP, Cluster.DEFAULT_GROUP);
            Configuration.Setup setup = config.addSetup(plugin, file, service, group);
            NodeList properties = setupElement.getChildNodes();
            for (int k = 0; k < properties.getLength(); ++k) {
               if (!(properties.item(k) instanceof Element)) continue;
               Element propertyElement = (Element) properties.item(k);
               assertName(ELEMENT_PROPERTY, propertyElement);
               String propertyName = getAttribute(propertyElement, ATTR_NAME);
               String content = propertyElement.getTextContent();
               if (content == null) {
                  throw new IllegalArgumentException("Property cannot have null content!");
               }
               setup.addProperty(propertyName, Evaluator.parseString(content.trim()));
            }
         }
         masterConfig.addConfig(config);
      }
   }

   private void parseScenario(Scenario scenario, Element scenarioElement) {
      assertName(ELEMENT_SCENARIO, scenarioElement);
      NodeList childNodes = scenarioElement.getChildNodes();
      for (int i = 0; i < childNodes.getLength(); i++) {
         Node child = childNodes.item(i);
         if (child instanceof Element) {
            addScenarioItem(scenario, (Element) child, Collections.EMPTY_MAP);
         }
      }
   }

   private Map<String, String> parseProperties(Element element) {
      NamedNodeMap attributes = element.getAttributes();
      Map<String, String> properties = new HashMap<String, String>();
      for (int attrIndex = 0; attrIndex < attributes.getLength(); attrIndex++) {
         Attr attr = (Attr) attributes.item(attrIndex);
         properties.put(attr.getName(), attr.getValue());
      }
      return properties;
   }

   private void parseReporting(MasterConfig masterConfig, Element reportsElement) {
      assertName(ELEMENT_REPORTS, reportsElement);
      NodeList childNodes = reportsElement.getChildNodes();
      for (int i = 0; i < childNodes.getLength(); i++) {
         if (!(childNodes.item(i) instanceof Element)) continue;
         Element reporterElement = (Element) childNodes.item(i);
         assertName(ELEMENT_REPORTER, reporterElement);
         String type = getAttribute(reporterElement, ATTR_TYPE);
         String run = getAttribute(reporterElement, ATTR_RUN, Reporter.RunCondition.ALWAYS.name());
         Reporter reporter = new Reporter(type, Reporter.RunCondition.valueOf(run.toUpperCase(Locale.ENGLISH)));
         NodeList reportElements = reporterElement.getChildNodes();
         Map<String, String> commonProperties = new HashMap<String, String>();
         for (int j = 0; j < reportElements.getLength(); ++j) {
            if (!(reportElements.item(j) instanceof Element)) continue;
            Element reportElement = (Element) reportElements.item(j);
            if (ELEMENT_REPORT.equals(reportElement.getNodeName())) {
               String source = getAttribute(reportElement, ATTR_SOURCE);
               Reporter.Report report = reporter.addReport(source);
               NodeList properties = reportElement.getChildNodes();
               for (int k = 0; k < properties.getLength(); ++k) {
                  if (!(properties.item(k) instanceof Element)) continue;
                  Element propertyElement = (Element) properties.item(k);
                  assertName(ELEMENT_PROPERTY, propertyElement);
                  String propertyName = getAttribute(propertyElement, ATTR_NAME);
                  String content = propertyElement.getTextContent();
                  if (content == null) {
                     throw new IllegalArgumentException("Property cannot have null content!");
                  }
                  report.addProperty(propertyName, Evaluator.parseString(content.trim()));
               }
            } else if (ELEMENT_PROPERTIES.equals(reportElement.getNodeName())) {
               NodeList properties = reportElement.getChildNodes();
               for (int k = 0; k < properties.getLength(); ++k) {
                  if (!(properties.item(k) instanceof Element)) continue;
                  Element propertyElement = (Element) properties.item(k);
                  assertName(ELEMENT_PROPERTY, propertyElement);
                  String propertyName = getAttribute(propertyElement, ATTR_NAME);
                  String content = propertyElement.getTextContent();
                  if (content == null) {
                     throw new IllegalArgumentException("Property cannot have null content!");
                  }
                  if (commonProperties.put(propertyName, Evaluator.parseString(content.trim())) != null) {
                     throw new IllegalArgumentException("Property '" + propertyName + "' already defined!");
                  }
               }
            } else {
               throwExpected(reportElement.getNodeName(), new String[] { ELEMENT_REPORT, ELEMENT_PROPERTIES });
            }
         }
         for (Reporter.Report report : reporter.getReports()) {
            for (Map.Entry<String, String> property : commonProperties.entrySet()) {
               report.addProperty(property.getKey(), property.getValue());
            }
         }
         if (reporter.getReports().isEmpty()) {
            throw new IllegalArgumentException("Reporter " + reporter.type + " must define at least one report.");
         } else {
            masterConfig.addReporter(reporter);
         }
      }
   }

   private void throwExpected(String found, String[] expected) {
      StringBuilder sb = new StringBuilder("Found '").append(found).append("', expected one of ");
      for (int i = 0; i < expected.length; ++i) {
         sb.append('\'').append(expected[i]).append('\'');
         if (i != expected.length - 1) sb.append(", ");
      }
      throw new IllegalArgumentException(sb.toString());
   }

   private void addScenarioItem(Scenario scenario, Element element, Map<String, String> extras) {
      if (element.getNodeName().equalsIgnoreCase(ELEMENT_REPEAT)) {
         addRepeat(scenario, element, extras);
      } else {
         scenario.addStage(StageHelper.getStageClassByDashedName(element.getNodeName()), parseProperties(element), extras);
      }
   }

   private void addRepeat(Scenario scenario, Element element, Map<String, String> extras) {
      String timesStr = getAttribute(element, ATTR_TIMES, "");
      String fromStr = getAttribute(element, ATTR_FROM, "");
      String toStr = getAttribute(element, ATTR_TO, "");
      String incStr = getAttribute(element, ATTR_INC, "");
      String repeatName = getAttribute(element, ATTR_NAME, "");
      if ((timesStr.isEmpty() && (fromStr.isEmpty() || toStr.isEmpty()))
            || (!timesStr.isEmpty() && (!fromStr.isEmpty() || !toStr.isEmpty() || !incStr.isEmpty()))) {
         throw new IllegalArgumentException("Define either times or from, to, [inc]");
      }
      int from = 0, to = 1, inc = 1;
      if (!timesStr.isEmpty()) {
         to = parseRepeatArg(timesStr, ATTR_TIMES, repeatName) - 1;
      } else {
         from = parseRepeatArg(fromStr, ATTR_FROM, repeatName);
         to = parseRepeatArg(toStr, ATTR_TO, repeatName);
         if (!incStr.isEmpty()) {
            inc = parseRepeatArg(incStr, ATTR_INC, repeatName);
         }
      }
      Map<String, String> repeatExtras = new HashMap<String, String>(extras);
      NodeList childNodes = element.getChildNodes();
      for (int counter = from; counter <= to; counter += inc) {
         String repeatProperty = PROPERTY_PREFIX_REPEAT + (repeatName.isEmpty() ? PROPERTY_SUFFIX_COUNTER : repeatName + '.' + PROPERTY_SUFFIX_COUNTER);
         repeatExtras.put(repeatProperty, String.valueOf(counter));
         for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child instanceof Element) {
               addScenarioItem(scenario, (Element) child, repeatExtras);
            }
         }
      }
   }

   private int parseRepeatArg(String value, String name, String repeatName) {
      try {
         return Integer.parseInt(value);
      } catch (NumberFormatException e) {
         throw new IllegalArgumentException(String.format("Attribute %s=%s on %s is not an integer!", name, value, repeatName != null ? repeatName : ELEMENT_REPEAT), e);
      }
   }
}

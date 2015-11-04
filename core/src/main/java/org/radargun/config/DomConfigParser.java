package org.radargun.config;

import org.radargun.Properties;
import org.radargun.ShutDownHook;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.ReporterHelper;
import org.radargun.stages.ScenarioCleanupStage;
import org.radargun.stages.ScenarioDestroyStage;
import org.radargun.stages.ScenarioInitStage;
import org.radargun.stages.control.RepeatBeginStage;
import org.radargun.stages.control.RepeatContinueStage;
import org.radargun.stages.control.RepeatEndStage;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class DomConfigParser extends ConfigParser implements ConfigSchema {
   private static Log log = LogFactory.getLog(DomConfigParser.class);
   private static final String ATTR_XMLNS = "xmlns";

   public MasterConfig parseConfig(String config) throws Exception {
      Document document;
      try {
         document = createDocumentBuilder().parse(config);
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }

      Element root = document.getDocumentElement();
      assertName(ELEMENT_BENCHMARK, root);

      NodeList childNodes = root.getChildNodes();
      int index = 0;
      index = nextElement(childNodes, index);
      MasterConfig masterConfig;
      if (ELEMENT_MASTER.equals(childNodes.item(index).getLocalName())) {
         masterConfig = parseMaster((Element) childNodes.item(index));
         index = nextElement(childNodes, index + 1);
      } else {
         masterConfig = new MasterConfig(0, null);
      }
      parseClusters(masterConfig, (Element) childNodes.item(index));
      index = nextElement(childNodes, index + 1);
      parseConfigurations(masterConfig, (Element) childNodes.item(index));
      index = nextElement(childNodes, index + 1);

      Scenario scenario = new Scenario();
      Map<String, Definition> initProperties = Collections.EMPTY_MAP;

      if (ELEMENT_INIT.equals(childNodes.item(index).getLocalName())) {
         initProperties = parseProperties(((Element) childNodes.item(index)), true);
         index = nextElement(childNodes, index + 1);
      }
      scenario.addStage(ScenarioInitStage.class, initProperties, null);

      Element scenarioElement;
      if (((Element) childNodes.item(index)).hasAttribute(ATTR_URL)) {
         scenarioElement = loadScenario(((Element) childNodes.item(index)).getAttribute(ATTR_URL));
      } else {
         scenarioElement = (Element) childNodes.item(index);
      }

      parseScenario(scenario, scenarioElement);
      masterConfig.setScenario(scenario);

      index = nextElement(childNodes, index + 1);

      Map<String, Definition> destroyProperties = Collections.EMPTY_MAP;
      Node elementDestroy = childNodes.item(index);
      if (elementDestroy != null && ELEMENT_DESTROY.equals(childNodes.item(index).getLocalName())) {
         destroyProperties = parseProperties((Element) childNodes.item(index), true);
         index = nextElement(childNodes, index + 1);
      }
      scenario.addStage(ScenarioDestroyStage.class, destroyProperties, null);

      Map<String, Definition> cleanupProperties = Collections.EMPTY_MAP;
      Node elementCleanup = childNodes.item(index);
      if (elementCleanup != null && ELEMENT_CLEANUP.equals(childNodes.item(index).getLocalName())) {
         cleanupProperties = parseProperties((Element) childNodes.item(index), true);
         index = nextElement(childNodes, index + 1);
      }
      scenario.addStage(ScenarioCleanupStage.class, cleanupProperties, null);

      Element reportElement = (Element) childNodes.item(index);
      if (reportElement != null && ELEMENT_REPORTS.equals(reportElement.getLocalName())) {
         parseReporting(masterConfig, reportElement);
      }

      return masterConfig;
   }

   protected DocumentBuilder createDocumentBuilder() throws ParserConfigurationException {
      DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
      documentBuilderFactory.setNamespaceAware(true);
      return documentBuilderFactory.newDocumentBuilder();
   }

   /**
    * Parses scenario, which is loaded from file or URL
    *
    * @param scenarioUri path or url of the scenario file, or benchmark file which contains scenario
    * @return parsed scenario element
    * @throws ParserConfigurationException if DocumentBuilder cannot be created
    */
   private Element loadScenario(String scenarioUri) throws ParserConfigurationException {
      Document document;
      try {
         document = createDocumentBuilder().parse(scenarioUri);
      } catch (Exception e) {
         throw new IllegalStateException("Could not parse imported scenario: " + scenarioUri, e);
      }

      Element root = document.getDocumentElement();
      if (ScenarioSchemaGenerator.NAMESPACE.equals(root.getNamespaceURI()) && ELEMENT_SCENARIO.equals(root.getLocalName())) {
         return root;
      } else if (BenchmarkSchemaGenerator.NAMESPACE.equals(root.getNamespaceURI()) && ELEMENT_BENCHMARK.equals(root.getLocalName())) {
         NodeList scenarios = document.getElementsByTagNameNS("*", ELEMENT_SCENARIO);
         if (scenarios.getLength() <= 0) {
            throw new IllegalArgumentException("Did not found any scenarios in " + scenarioUri);
         } else if (scenarios.getLength() > 1) {
            throw new IllegalArgumentException("Found multiple scenarios in " + scenarioUri);
         } else {
            return (Element) scenarios.item(0);
         }
      } else {
         throw new IllegalStateException(String.format("Unexpected root element in %s: namespace=%s, local-name=%s",
               scenarioUri, root.getNamespaceURI(), root.getLocalName()));
      }
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
      if (!name.equals(element.getLocalName())) {
         throw new IllegalArgumentException("Found '" + element.getLocalName() + "', expected '" + name + "'");
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
      if (!ELEMENT_CLUSTERS.equals(clustersElement.getLocalName())) {
         throw unexpected(clustersElement.getLocalName(), new String[]{ELEMENT_CLUSTERS});
      }
      if (masterConfig.getPort() == 0 || masterConfig.getHost() == null) {
         throw new IllegalArgumentException("Master not configured for distributed scenario!");
      }
      String clusterSizeBackup = System.getProperty(Properties.PROPERTY_CLUSTER_SIZE);
      NodeList clusters = clustersElement.getChildNodes();
      for (int i = 0; i < clusters.getLength(); ++i) {
         if (!(clusters.item(i) instanceof Element)) continue;
         Element childElement = (Element) clusters.item(i);
         if (ELEMENT_CLUSTER.equals(childElement.getLocalName())) {
            int size = Integer.parseInt(getAttribute(childElement, ATTR_SIZE, "0"));
            System.setProperty(Properties.PROPERTY_CLUSTER_SIZE, String.valueOf(size));
            addCluster(masterConfig, childElement, size);
         } else if (ELEMENT_SCALE.equals(childElement.getLocalName())) {
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
            throw unexpected(childElement.getLocalName(), new String[]{ELEMENT_CLUSTER, ELEMENT_SCALE});
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
            String group = getAttribute(setupElement, ATTR_GROUP, Cluster.DEFAULT_GROUP);
            Map<String, Definition> propertyDefinitions = Collections.EMPTY_MAP;
            Map<String, Definition> vmArgs = Collections.EMPTY_MAP;
            String service = Configuration.DEFAULT_SERVICE;
            NodeList properties = setupElement.getChildNodes();
            for (int k = 0; k < properties.getLength(); ++k) {
               if (!(properties.item(k) instanceof Element)) continue;
               Element setupChildElement = (Element) properties.item(k);
               if (ELEMENT_VM_ARGS.equals(setupChildElement.getLocalName())) {
                  vmArgs = parseProperties(setupChildElement, true);
               } else if (setupChildElement.hasAttribute(ATTR_XMLNS)) {
                  service = setupChildElement.getLocalName();
                  propertyDefinitions = parseProperties(setupChildElement, true);
               } else {
                  throw notExternal(setupChildElement);
               }
            }
            config.addSetup(group, plugin, service, propertyDefinitions, vmArgs);
         }
         masterConfig.addConfig(config);
      }
   }

   private IllegalArgumentException notExternal(Element element) {
      return new IllegalArgumentException("Element " + element.getLocalName() + " does not refer to any external schema.");
   }

   private void parseScenario(Scenario scenario, Element scenarioElement) {
      assertName(ELEMENT_SCENARIO, scenarioElement);
      NodeList childNodes = scenarioElement.getChildNodes();
      for (int i = 0; i < childNodes.getLength(); i++) {
         Node child = childNodes.item(i);
         if (child instanceof Element) {
            addScenarioItem(scenario, (Element) child);
         }
      }
   }

   private Map<String, Definition> parseProperties(Element element, boolean subElements) {
      NamedNodeMap attributes = element.getAttributes();
      Map<String, Definition> properties = new HashMap<String, Definition>();
      for (int attrIndex = 0; attrIndex < attributes.getLength(); attrIndex++) {
         Attr attr = (Attr) attributes.item(attrIndex);
         if (ATTR_XMLNS.equals(attr.getName())) continue;
         properties.put(attr.getName(), new SimpleDefinition(attr.getValue(), SimpleDefinition.Source.ATTRIBUTE));
      }
      if (!subElements) {
         return properties;
      }
      NodeList children = element.getChildNodes();
      for (int childIndex = 0; childIndex < children.getLength(); ++childIndex) {
         Node n = children.item(childIndex);
         if (n instanceof Element) {
            Element childElement = (Element) n;
            properties.put(childElement.getLocalName(), toDefinition(childElement));
         } else if (n instanceof Text) {
            String text = ((Text) n).getWholeText().trim();
            if (!text.isEmpty()) {
               throw new IllegalArgumentException("Non parsed text: " + text);
            }
         } else if (n instanceof Comment) {
            continue;
         } else {
            throw new IllegalArgumentException("Unexpected content: " + n);
         }
      }
      return properties;
   }

   private Definition toDefinition(Element element) {
      NamedNodeMap attributes = element.getAttributes();
      NodeList children = element.getChildNodes();
      if (attributes.getLength() == 0 && children.getLength() == 1 && children.item(0) instanceof Text) {
         return new SimpleDefinition(((Text) children.item(0)).getWholeText(), SimpleDefinition.Source.TEXT);
      }
      ComplexDefinition definition = new ComplexDefinition();
      for (int i = 0; i < attributes.getLength(); ++i) {
         Attr attr = (Attr) attributes.item(i);
         if (ATTR_XMLNS.equals(attr.getName())) {
            definition.setNamespace(attr.getValue());
            continue;
         }
         definition.add(attr.getName(), new SimpleDefinition(attr.getValue(), SimpleDefinition.Source.ATTRIBUTE));
      }
      for (int i = 0; i < children.getLength(); ++i) {
         Node n = children.item(i);
         if (n instanceof Element) {
            definition.add(n.getLocalName(), toDefinition((Element) n));
         } else if (n instanceof Text) {
            String text = ((Text) n).getWholeText().trim();
            if (!text.isEmpty()) {
               definition.add("", new SimpleDefinition(text, SimpleDefinition.Source.TEXT));
            }
         } else if (n instanceof Comment) {
            continue;
         } else {
            throw new IllegalArgumentException("Unexpected content: " + n);
         }
      }
      return definition;
   }

   private void parseReporting(MasterConfig masterConfig, Element reportsElement) {
      assertName(ELEMENT_REPORTS, reportsElement);
      NodeList childNodes = reportsElement.getChildNodes();
      for (int i = 0; i < childNodes.getLength(); i++) {
         if (!(childNodes.item(i) instanceof Element)) continue;
         Element reporterElement = (Element) childNodes.item(i);
         assertName(ELEMENT_REPORTER, reporterElement);
         String type = getAttribute(reporterElement, ATTR_TYPE);
         ReporterConfiguration reporter = new ReporterConfiguration(type);
         NodeList reportElements = reporterElement.getChildNodes();
         Map<String, Definition> commonProperties = new HashMap<>();
         Set<String> reporterNames = ReporterHelper.getReporterNames();
         String[] expectedReporters = reporterNames.toArray(new String[reporterNames.size()]);
         String[] expectedElements = reporterNames.toArray(new String[reporterNames.size() + 1]);
         expectedElements[expectedElements.length - 1] = ELEMENT_REPORT;
         for (int j = 0; j < reportElements.getLength(); ++j) {
            if (!(reportElements.item(j) instanceof Element)) continue;
            Element element = (Element) reportElements.item(j);
            if (ELEMENT_REPORT.equals(element.getLocalName())) {
               ReporterConfiguration.Report report = reporter.addReport();
               NodeList reportChildren = element.getChildNodes();
               for (int k = 0; k < reportChildren.getLength(); ++k) {
                  if (!(reportChildren.item(k) instanceof Element)) continue;
                  Element childElement = (Element) reportChildren.item(k);
                  for (Map.Entry<String, Definition> property : parseReportProperties(type, childElement, expectedReporters).entrySet()) {
                     report.addProperty(property.getKey(), property.getValue());
                  }
               }
            } else {
               for (Map.Entry<String, Definition> property : parseReportProperties(type, element, expectedElements).entrySet()) {
                  commonProperties.put(property.getKey(), property.getValue());
               }
            }
         }
         if (reporter.getReports().isEmpty()) {
            reporter.addReport(); // default one
         }
         for (ReporterConfiguration.Report report : reporter.getReports()) {
            for (Map.Entry<String, Definition> property : commonProperties.entrySet()) {
               if (report.isPropertyDefined(property.getKey())) continue;
               report.addProperty(property.getKey(), property.getValue());
            }
         }
         masterConfig.addReporter(reporter);
      }
   }

   private Map<String, Definition> parseReportProperties(String type, Element element, String[] expectedElements) {
      if (!element.hasAttribute(ATTR_XMLNS)) {
         throw notExternal(element);
      } else if (!type.equals(element.getLocalName())) {
         throw new IllegalArgumentException("Expecting reporter type " + type + " but " + element.getLocalName() + " was used.");
      } else if (ReporterHelper.isRegistered(element.getLocalName())) {
         return parseProperties(element, true);
      } else {
         throw unexpected(element.getLocalName(), expectedElements);
      }
   }

   private IllegalArgumentException unexpected(String found, String[] expected) {
      StringBuilder sb = new StringBuilder("Found '").append(found).append("', expected one of ");
      for (int i = 0; i < expected.length; ++i) {
         sb.append('\'').append(expected[i]).append('\'');
         if (i != expected.length - 1) sb.append(", ");
      }
      return new IllegalArgumentException(sb.toString());
   }

   private void addScenarioItem(Scenario scenario, Element element) {
      if (element.getLocalName().equalsIgnoreCase(ELEMENT_REPEAT)) {
         wrapStages(scenario, element, new Class[]{RepeatBeginStage.class}, new Class[]{RepeatContinueStage.class, RepeatEndStage.class});
      } else {
         scenario.addStage(StageHelper.getStageClassByDashedName(element.getNamespaceURI(), element.getLocalName()), parseProperties(element, true), null);
      }
   }

   private void wrapStages(Scenario scenario, Element element,
                           Class<? extends org.radargun.Stage>[] stagesBefore, Class<? extends org.radargun.Stage>[] stagesAfter) {
      String labelName = getAttribute(element, ATTR_NAME, "");
      Map<String, Definition> properties = parseProperties(element, false);
      for (Class<? extends org.radargun.Stage> stage : stagesBefore) {
         scenario.addStage(stage, properties, labelName);
      }
      NodeList childNodes = element.getChildNodes();
      for (int i = 0; i < childNodes.getLength(); i++) {
         Node child = childNodes.item(i);
         if (child instanceof Element) {
            addScenarioItem(scenario, (Element) child);
         }
      }
      for (Class<? extends org.radargun.Stage> stage : stagesAfter) {
         scenario.addStage(stage, properties, labelName);
      }
   }

   /**
    * Parses
    *
    * @param args
    */
   public static void main(String[] args) {
      if (args.length < 2) {
         System.err.println("Usage: DomConfigParser config-file method");
         ShutDownHook.exit(1);
      }
      MasterConfig config = null;
      try {
         config = getConfigParser().parseConfig(args[0]);
      } catch (Exception e) {
         System.err.printf("Failed to load config '%s'%n", args[0]);
         ShutDownHook.exit(1);
      }
      Method method = null;
      try {
         method = config.getClass().getMethod(args[1]);
      } catch (NoSuchMethodException e) {
         System.err.printf("No method '%s.%s()'%n", config.getClass().getName(), args[1]);
         ShutDownHook.exit(1);
      }
      try {
         Object result = method.invoke(config);
         System.out.println(String.valueOf(result));
      } catch (Exception e) {
         System.err.println("Failed to invoke method " + method);
         e.printStackTrace(System.err);
         ShutDownHook.exit(1);
      }
   }
}

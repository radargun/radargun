package org.radargun.config;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Attr;
import org.radargun.stages.GenerateChartStage;
import org.radargun.stages.StartClusterStage;
import org.radargun.Master;
import org.radargun.Stage;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.Map;
import java.util.HashMap;

/**
 * @author Mircea.Markus@jboss.com
 */
public class DomConfigParser extends ConfigParser {
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

      Element configRoot = (Element) document.getElementsByTagName("bench-config").item(0);

      ScalingBenchmarkConfig prototype = buildBenchmarkPrototype(configRoot);
      MasterConfig masterConfig = parseMaster(configRoot, prototype);

      parseProductsElement(configRoot, prototype, masterConfig);

      //now add the reporting
      parseReporting(configRoot, masterConfig);

      return masterConfig;

   }


   private void parseReporting(Element configRoot, MasterConfig masterConfig) {
      Element reportsEl = (Element) configRoot.getElementsByTagName("reports").item(0);
      NodeList reportElList = reportsEl.getElementsByTagName("report");
      FixedSizeBenchmarkConfig reportBenchmark = new FixedSizeBenchmarkConfig();

      masterConfig.addBenchmark(reportBenchmark);
      for (int i = 0; i < reportElList.getLength(); i++) {
         if (reportElList.item(i) instanceof Element) {
            Element thisReportEl = (Element) reportElList.item(i);
            GenerateChartStage generateChartStage = new GenerateChartStage();
            reportBenchmark.addStage(generateChartStage);
            generateChartStage.setFnPrefix(ConfigHelper.getStrAttribute(thisReportEl, "name"));
            if (thisReportEl.getAttribute("includeAll") != null) {
               String inclAll = ConfigHelper.getStrAttribute(thisReportEl, "includeAll");
               if (inclAll.equalsIgnoreCase("true"))
                  continue;
            }

            NodeList itemsEl = thisReportEl.getElementsByTagName("item");
            for (int j = 0; j < itemsEl.getLength(); j++) {
               Element itemEl = (Element) itemsEl.item(j);
               String productName = ConfigHelper.getStrAttribute(itemEl, "product");
               String productConfig = ConfigHelper.getStrAttribute(itemEl, "config");
               generateChartStage.addReportFilter(productName, productConfig);
            }
         }
      }
   }

   private void parseProductsElement(Element configRoot, ScalingBenchmarkConfig prototype, MasterConfig masterConfig) {
      Element productsEl = (Element) configRoot.getElementsByTagName("products").item(0);
      NodeList productsChildEl = productsEl.getChildNodes();
      for (int i = 0; i < productsChildEl.getLength(); i++) {
         Node node = productsChildEl.item(i);
         if (node instanceof Element) {
            Element nodeEl = (Element) node;
            String productName = nodeEl.getNodeName();
            NodeList configs = nodeEl.getElementsByTagName("config");
            for (int configIndex = 0; configIndex < configs.getLength(); configIndex++) {
               Element configEl = (Element) configs.item(configIndex);
               String configName = configEl.getAttribute("name");

               ScalingBenchmarkConfig clone = prototype.clone();
               updateStartupStage(configName, clone);
               clone.setProductName(productName);
               masterConfig.addBenchmark(clone);
               clone.setConfigName(configName);
            }

         }
      }
   }

   private MasterConfig parseMaster(Element configRoot, ScalingBenchmarkConfig prototype) {
      MasterConfig masterConfig;
      Element masterEl = (Element) configRoot.getElementsByTagName("master").item(0);
      String bindAddress = ConfigHelper.getStrAttribute(masterEl, "bindAddress");
      int port = masterEl.getAttribute("port") != null ? ConfigHelper.getIntAttribute(masterEl, "port") : Master.DEFAULT_PORT;
      masterConfig = new MasterConfig(port, bindAddress, prototype.getMaxSize());
      return masterConfig;
   }

   private void updateStartupStage(String configName, ScalingBenchmarkConfig clone) {
      for (Stage st : clone.getStages()) {
         if (st instanceof StartClusterStage) {
            StartClusterStage scs = (StartClusterStage) st;
            scs.setConfig(configName);
         }
      }
   }

   private ScalingBenchmarkConfig buildBenchmarkPrototype(Element configRoot) {
      ScalingBenchmarkConfig prototype;
      prototype = new ScalingBenchmarkConfig();
      Element benchmarkEl = (Element) configRoot.getElementsByTagName("benchmark").item(0);
      prototype.setInitSize(ConfigHelper.getIntAttribute(benchmarkEl, "initSize"));
      prototype.setMaxSize(ConfigHelper.getIntAttribute(benchmarkEl, "maxSize"));
      prototype.setIncrement(ConfigHelper.getIntAttribute(benchmarkEl, "increment"));

      NodeList childNodes = benchmarkEl.getChildNodes();
      for (int i = 0; i < childNodes.getLength(); i++) {
         Node child = childNodes.item(i);
         if (child instanceof Element) {
            Element childEl = (Element) child;
            String stageShortName = childEl.getNodeName();
            Stage st = JaxbConfigParser.getStage(stageShortName + "Stage");
            prototype.addStage(st);
            NamedNodeMap attributes = childEl.getAttributes();
            Map<String, String> attrToSet = new HashMap<String, String>();
            for (int attrIndex = 0; attrIndex < attributes.getLength(); attrIndex++) {
               Attr attr = (Attr) attributes.item(attrIndex);
               attrToSet.put(attr.getName(), ConfigHelper.parseString(attr.getValue()));
            }
            ConfigHelper.setValues(st, attrToSet, true);
         }
      }
      return prototype;
   }

}

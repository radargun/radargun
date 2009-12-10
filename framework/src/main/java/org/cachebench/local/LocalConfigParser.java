package org.cachebench.local;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapperStressor;
import org.cachebench.config.ConfigHelper;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Mircea.Markus@jboss.com
 */
public class LocalConfigParser {

   private static Log log = LogFactory.getLog(LocalConfigParser.class);


   public LocalBenchmark parse(String config) throws Exception {
      DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      Document document;
      try {
         document = builder.parse(config);
      }
      catch (Exception e) {
         throw new IllegalStateException(e);
      }

      LocalBenchmark result = new LocalBenchmark();
      Element configRoot = (Element) document.getElementsByTagName("local-bench-config").item(0);
      parseBenchmarkElement(result, configRoot);
      parseProductsElement(configRoot, result);
      parseReporting(configRoot, result);
      return result;
   }

   private void parseBenchmarkElement(LocalBenchmark result, Element configRoot) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
      Element benchmark = (Element) configRoot.getElementsByTagName("benchmark").item(0);
      for (int i = 0; i < benchmark.getChildNodes().getLength(); i++) {
         Node node = benchmark.getChildNodes().item(i);
         if (node instanceof Element) {
            Element nodeEl = (Element) node;
            String stressorName = "org.cachebench.stressors." + nodeEl.getNodeName() + "Stressor";
            CacheWrapperStressor stressor = (CacheWrapperStressor) Class.forName(stressorName).newInstance();
            Map<String, String> attrValues = new HashMap<String, String>();
            for (int j = 0; j < nodeEl.getAttributes().getLength(); j++) {
               Attr attr = (Attr) nodeEl.getAttributes().item(j);
               attrValues.put(attr.getName(), attr.getValue());
            }
            ConfigHelper.setValues(stressor, attrValues, true);
            result.addStressor(stressor);
         }
      }
   }

   private void parseProductsElement(Element configRoot, LocalBenchmark localBenchmark) {
      Element productsEl = (Element) configRoot.getElementsByTagName("products").item(0);
      NodeList productsChildEl = productsEl.getChildNodes();
      for (int i = 0; i < productsChildEl.getLength(); i++) {
         Node node = productsChildEl.item(i);
         if (node instanceof Element) {
            Element nodeEl = (Element) node;
            String productName = nodeEl.getNodeName();
            NodeList configs = nodeEl.getElementsByTagName("config");
            List<String> configNames = new ArrayList<String>();

            for (int configIndex = 0; configIndex < configs.getLength(); configIndex++) {
               Element configEl = (Element) configs.item(configIndex);
               String configName = configEl.getAttribute("name");
               configNames.add(configName);
            }
            localBenchmark.addProductConfig(productName, configNames);
         }
      }
   }


   private void parseReporting(Element configRoot, LocalBenchmark localBenchmark) {
      Element reportsEl = (Element) configRoot.getElementsByTagName("reports").item(0);
      NodeList reportElList = reportsEl.getElementsByTagName("report");
      for (int i = 0; i < reportElList.getLength(); i++) {
         if (reportElList.item(i) instanceof Element) {
            ReportDesc reportDesc = new ReportDesc();
            Element thisReportEl = (Element) reportElList.item(i);
            if (thisReportEl.getAttribute("includeAll") != null) {
               String inclAll = ConfigHelper.getStrAttribute(thisReportEl, "includeAll");
               if (inclAll.equalsIgnoreCase("true")) {
                  reportDesc.setIncludeAll(true);
                  localBenchmark.addReportDesc(reportDesc);
                  reportDesc.setReportName(ConfigHelper.getStrAttribute(thisReportEl, "name"));
                  continue;
               }
            }

            NodeList itemsEl = thisReportEl.getElementsByTagName("item");
            for (int j = 0; j < itemsEl.getLength(); j++) {
               Element itemEl = (Element) itemsEl.item(j);
               String productName = ConfigHelper.getStrAttribute(itemEl, "product");
               String productConfig = ConfigHelper.getStrAttribute(itemEl, "config");
               reportDesc.addReportItem(productName, productConfig);
            }
            reportDesc.setReportName(ConfigHelper.getStrAttribute(thisReportEl, "name"));
            localBenchmark.addReportDesc(reportDesc);
         }
      }
   }


}

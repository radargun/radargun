package org.cachebench;

import com.sun.org.apache.xerces.internal.parsers.DOMParser;
import org.cachebench.config.ConfigHelper;
import org.cachebench.config.FixedSizeBenchmarkConfig;
import org.cachebench.config.MasterConfig;
import org.cachebench.config.ScalingBenchmarkConfig;
import org.cachebench.config.jaxb.BenchConfig;
import org.cachebench.stages.GenerateChartStage;
import org.cachebench.stages.StartClusterStage;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * // TODO: Mircea - Document this!
 *
 * @author Mircea.Markus@jboss.com
 */
public class LaunchMaster {

   public static void main(String[] args) throws Exception {

      File currentDir = new File(".");
      System.out.println("Runnign in directory: " + currentDir.getAbsolutePath());

      String config = getConfigOrExit(args);

      if (System.getProperties().contains("cachebench.oldLauncher")) {
         launchOld(config);
         return;
      }

      //the content in the new file is too dynamic, let's just use DOM for now

      DOMParser parser = new DOMParser();
      try {
         parser.parse(config);
         System.out.println(config + " is well-formed.");
      }
      catch (SAXException e) {
         System.out.println(config + " is not well-formed.");
      }
      catch (IOException e) {
         e.printStackTrace();
         System.out.println(
               "Due to an IOException, the parser could not check "
                     + config
         );
      }

      Document document = parser.getDocument();
      Element configRoot = (Element) document.getElementsByTagName("bench-config").item(0);

      ScalingBenchmarkConfig prototype = buildBenchmarkPrototype(configRoot);
      MasterConfig masterConfig = parseMaster(configRoot, prototype);

      parseBenchmarks(configRoot, prototype, masterConfig);

      //now add the reporting
      parseReporting(configRoot, masterConfig);

      Master server = new Master(masterConfig);
      server.start();

   }

   private static void parseReporting(Element configRoot, MasterConfig masterConfig) {
      Element reportsEl = (Element) configRoot.getElementsByTagName("reports").item(0);
      NodeList reportElList = reportsEl.getElementsByTagName("report");
      FixedSizeBenchmarkConfig reportBenchmark = new FixedSizeBenchmarkConfig();

      masterConfig.addBenchmark(reportBenchmark);
      for (int i = 0; i < reportElList.getLength(); i++) {
         if (reportElList.item(i) instanceof Element) {
            Element thisReportEl = (Element) reportElList.item(i);
            GenerateChartStage generateChartStage = new GenerateChartStage();
            reportBenchmark.addStage(generateChartStage);
            generateChartStage.setFnPrefix(getStrAttribute(thisReportEl, "name"));
            if (thisReportEl.getAttribute("includeAll") != null) {
               String inclAll = getStrAttribute(thisReportEl, "includeAll");
               if (inclAll.equalsIgnoreCase("true"))
                  continue;
            }

            NodeList productsEl = thisReportEl.getChildNodes();
            for (int j = 0; j < productsEl.getLength(); j++) {
               Node product = productsEl.item(j);
               if (product instanceof Element) {
                  Element productEl = (Element) product;
                  String productName = productEl.getNodeName();
                  NodeList configs = productEl.getElementsByTagName("config");
                  for (int z = 0; z < configs.getLength(); z++) {
                     Element configEl = (Element) configs.item(z);
                     String filterName = getStrAttribute(configEl, "name");
                     generateChartStage.addReportFilter(productName, filterName);
                  }
               }
            }
         }
      }
   }

   private static void parseBenchmarks(Element configRoot, ScalingBenchmarkConfig prototype, MasterConfig masterConfig) {
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

   private static MasterConfig parseMaster(Element configRoot, ScalingBenchmarkConfig prototype) {
      MasterConfig masterConfig;
      Element masterEl = (Element) configRoot.getElementsByTagName("master").item(0);
      String bindAddress = getStrAttribute(masterEl, "bindAddress");
      int port = masterEl.getAttribute("port") != null ? getIntAttribute(masterEl, "port") : Master.DEFAULT_PORT;
      masterConfig = new MasterConfig(port, bindAddress, prototype.getMaxSize());
      return masterConfig;
   }

   private static void updateStartupStage(String productName, ScalingBenchmarkConfig clone) {
      for (Stage st : clone.getStages()) {
         if (st instanceof StartClusterStage) {
            StartClusterStage scs = (StartClusterStage) st;
            Map<String, String> configs = new HashMap<String, String>();
            configs.put("config", productName);
            scs.setWrapperStartupParams(configs);
         }
      }
   }

   private static ScalingBenchmarkConfig buildBenchmarkPrototype(Element configRoot) {
      ScalingBenchmarkConfig prototype;
      prototype = new ScalingBenchmarkConfig();
      Element benchmarkEl = (Element) configRoot.getElementsByTagName("benchmark").item(0);
      prototype.setInitSize(getIntAttribute(benchmarkEl, "initSize"));
      prototype.setMaxSize(getIntAttribute(benchmarkEl, "maxSize"));
      prototype.setIncrement(getIntAttribute(benchmarkEl, "increment"));

      NodeList childNodes = benchmarkEl.getChildNodes();
      for (int i = 0; i < childNodes.getLength(); i++) {
         Node child = childNodes.item(i);
         if (child instanceof Element) {
            Element childEl = (Element) child;
            String stageShortName = childEl.getNodeName();
            Stage st = ConfigHelper.getStage(stageShortName + "Stage");
            prototype.addStage(st);
            NamedNodeMap attributes = childEl.getAttributes();
            Map<String, String> attrToSet = new HashMap<String, String>();
            for (int attrIndex = 0; attrIndex < attributes.getLength(); attrIndex++) {
               Attr attr = (Attr) attributes.item(attrIndex);
               attrToSet.put(attr.getName(), ConfigHelper.parseString(attr.getValue()));
            }
            ConfigHelper.setValues(st, attrToSet, false, true);
         }
      }
      return prototype;
   }

   private static String getStrAttribute(Element master, String attrName) {
      String s = master.getAttribute(attrName);
      return ConfigHelper.parseString(s);
   }

   private static int getIntAttribute(Element master, String attrName) {
      String s = master.getAttribute(attrName);
      return Integer.parseInt(ConfigHelper.parseString(s));
   }

   private static String getConfigOrExit(String[] args) {
      String config = null;
      for (int i = 0; i < args.length - 1; i++) {
         if (args[i].equals("-config")) {
            config = args[i + 1];
         }
      }
      if (config == null) {
         printUsageAndExit();
      }
      return config;
   }

   private static void launchOld(String config) throws Exception {
      File configFile = new File(config);
      if (!configFile.exists()) {
         System.err.println("No such file: " + configFile.getAbsolutePath());
         printUsageAndExit();
      }


      JAXBContext jc = JAXBContext.newInstance("org.cachebench.config.jaxb");
      Unmarshaller unmarshaller = jc.createUnmarshaller();
      BenchConfig benchConfig = (BenchConfig) unmarshaller.unmarshal(configFile);
      Master server = ConfigHelper.getMaster(benchConfig);
      server.start();
   }


   private static void printUsageAndExit() {
      System.out.println("Usage: start_master.sh  -config <config-file.xml>");
      System.out.println("       -config : xml file containing benchmark's configuration");
      System.exit(1);
   }
}

package org.radargun.reporting.horreum;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.hyperfoil.tools.HorreumClient;
import io.hyperfoil.tools.horreum.entity.json.Access;
import org.radargun.config.Configuration;
import org.radargun.config.MainConfig;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.AbstractReporter;
import org.radargun.reporting.Report;
import org.radargun.reporting.commons.DataReporter;
import org.radargun.utils.KeyValueListConverter;

public class HorreumReporter extends AbstractReporter {

   private static final Log LOG = LogFactory.getLog(HorreumReporter.class);

   private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

   @Property(doc = "horreumUrl")
   private String horreumUrl = "localhost";

   @Property(doc = "keycloakUrl")
   private String keycloakUrl = "localhost";

   @Property(doc = "keycloakRealm")
   private String keycloakRealm = "horreum";

   @Property(doc = "horreumUser")
   private String horreumUser;

   @Property(doc = "horreumPassword")
   private String horreumPassword;

   @Property(doc = "clientId")
   private String clientId = "horreum-ui";

   @Property(doc = "horreumTest")
   private String horreumTest;

   @Property(doc = "horreumOwner")
   private String horreumOwner;

   @Property(doc = "horreumAccess")
   private String horreumAccess;

   @Property(doc = "horreumSchema")
   private String horreumSchema = "urn:radargun:0.1";

   @Property(doc = "horreumHttpsCertificate")
   private String horreumHttpsCertificate;

   @Property(doc = "Compute response times at certain percentiles.")
   private double[] percentiles = new double[] {50d, 60d, 70d, 80d, 85d, 90d, 95d, 99d, 99.9d, 99.99d, 99.999d, 99.9999d};

   @Property(doc = "Directory into which will be report files written.")
   private String targetDir = "results" + File.separator + "json";

   @Property(doc = "Workers whose results will be ignored.")
   private Set<Integer> ignore;

   @Property(doc = "Tags", converter = KeyValueListConverter.class)
   private Map<String, String> tags = new HashMap<>();

   @Property(doc = "Additional build parameters", converter = KeyValueListConverter.class)
   private Map<String, String> buildParams = new HashMap<>();

   @Property(doc = "File (in java properties format) from which to load additional build parameters")
   private String buildParamsFile = null;

   private HorreumClient horreumClient;

   @Override
   public void run(MainConfig mainConfig, Collection<Report> reports) throws Exception {
      if (horreumClient == null) {
         HorreumClient.Builder builder = new HorreumClient.Builder()
               .horreumUrl(horreumUrl)
               .keycloakUrl(keycloakUrl)
               .keycloakRealm(keycloakRealm)
               .horreumUser(horreumUser)
               .horreumPassword(horreumPassword)
               .clientId(clientId);
         if (horreumHttpsCertificate != null) {
            builder.sslContext(horreumHttpsCertificate);
         }
         horreumClient = builder.build();
      }
      for (Report report : reports) {
         for (Report.Test test : report.getTests()) {
            reportTestsV2(report, test);
         }
      }
   }

   private void reportTestsV2(Report report, Report.Test test) {
      File outputFile;
      try {
         outputFile = DataReporter.prepareOutputFile(report, test.name, "", targetDir, "json");
         DataReporter.DataReportValue dataValue = DataReporter.get(test, ignore, false, percentiles);

         String start = DATE_FORMAT.format(dataValue.firstTimestamp);
         String stop = DATE_FORMAT.format(dataValue.lastTimestamp);
         String token = null;
         String description = null;

         Map<String, Object> jsonData = new HashMap<>();
         jsonData.put("data", convertData(dataValue.rows));
         jsonData.put("tags", getTags(test));
         jsonData.put("start", start);
         jsonData.put("stop", stop);
         jsonData.put("buildParams", getBuildParams());
         jsonData.put("$schema", horreumSchema);

         ObjectMapper mapper = new ObjectMapper();
         ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
         JsonNode data = mapper.convertValue(jsonData, new TypeReference<>(){});
         writer.writeValue(outputFile, data);

         Response response = horreumClient.runService.addRunFromData(start, stop, horreumTest, horreumOwner, Access.valueOf(horreumAccess), token, horreumSchema, description, data);
         String responseBody = response.readEntity(String.class);
         if (response.getStatus() != 200) {
            LOG.error(String.format("Horreum response(%d): %s", response.getStatus(), responseBody));
         } else {
            LOG.info(String.format("Horreum response(%d): %s", response.getStatus(), responseBody));
         }
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   private Map<String, Object> getTags(Report.Test test) {
      Map<String, Object> tagMap = new HashMap<>();
      tagMap.put("testName", test.name);
      tagMap.put("size", test.getReport().getCluster().getSize());
      tagMap.put("configName", test.getReport().getConfiguration().getName());
      for (Configuration.Setup setup : test.getReport().getConfiguration().getSetups()) {
         tagMap.put("setupPlugin", setup.plugin);
         tagMap.put("setupService", setup.service);
      }
      tags.forEach((k, v) -> tagMap.put(k, v));
      return tagMap;
   }

   private Map<String, String> getBuildParams() {
      Map<String, String> params = new HashMap<>();
      if (buildParams != null) {
         for (Map.Entry<String, String> buildParam : buildParams.entrySet()) {
            params.put(buildParam.getKey(), buildParam.getValue());
         }
      }
      if (buildParamsFile != null) {
         Properties paramsFromFile = new Properties();
         try (FileInputStream fileInputStream = new FileInputStream(buildParamsFile)) {
            paramsFromFile.load(fileInputStream);
         } catch (IOException e) {
            throw new IllegalStateException(e);
         }
         for (Map.Entry<Object, Object> entry : paramsFromFile.entrySet()) {
            params.put((String) entry.getKey(), (String) entry.getValue());
         }
      }
      return params;
   }

   private List<Map<String, Double>> convertData(List<Map<String, String>> values) {
      List<Map<String, Double>> convertedValues = new ArrayList<>();
      for (Map<String, String> map : values) {
         Map<String, Double> newMap = new HashMap<>();
         for (String key : map.keySet()) {
            newMap.put(key, Double.valueOf(map.get(key)));
         }
         convertedValues.add(newMap);
      }
      return convertedValues;
   }
}

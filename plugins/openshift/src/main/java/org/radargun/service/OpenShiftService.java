package org.radargun.service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.DoneableTemplate;
import io.fabric8.openshift.api.model.Template;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.ParameterValue;
import io.fabric8.openshift.client.dsl.TemplateResource;
import org.radargun.Service;
import org.radargun.ServiceHelper;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.ArgsConverter;
import org.radargun.utils.EnvsConverter;
import org.radargun.utils.TimeService;

/**
 *
 * @author Martin Gencur
 */
@Service(doc = OpenShiftService.SERVICE_DESCRIPTION)
public class OpenShiftService implements Lifecycle {

   protected final Log log = LogFactory.getLog(getClass());
   protected static final String SERVICE_DESCRIPTION = "OpenShift Service";

   private boolean started = false;
   private OpenShiftClient openShiftClient;
   private KubernetesList createdObjects;
   private PodCluster cluster;

   @Property(doc = "The template which contains definitions of Pods, Services, etc.")
   protected String templateFile = "template.json";

   @Property(doc = "Parameters for the template.", converter = EnvsConverter.class)
   protected Map<String, String> params = Collections.emptyMap();

   @Property(doc = "Selector for pods. The service will wait for the pods to be ready before proceeding.", converter = EnvsConverter.class)
   protected Map<String, String> podsSelector = Collections.emptyMap();

   @Property(doc = "URL of OpenShift master. Default is 127.0.0.1:8443.", optional = false)
   protected String masterUrl = "127.0.0.1:8443";

   @Property(doc = "OpenShift namespace to be used. Default is myproject.")
   protected String namespace = "myproject";

   @Property(doc = "OAUTH token to be used for authentication.")
   protected String oauthToken;

   @Property(doc = "Username to be used to connect to OpenShift. Default is an empty value which can be used only when OAUTH parameter is specified.")
   protected String username;

   @Property(doc = "Password to be used to connect to OpenShift. Default is an empty value which can be used only when OAUTH parameter is specified.")
   protected String password;

   @Property(doc = "Perform cleanOpenShiftNamespace of the OpenShift namespace after the tests finish.")
   protected boolean cleanup;

   @Property(doc = "Names of Pods whose IP addresses will be resolved by OpenShift client. " +
                   "The addresses will be stored in ServiceHelper#ServiceContext. Empty by default.",
             converter = ArgsConverter.class)
   protected List<String> resolvePodAddresses = Collections.emptyList();

   @Property(doc = "Names of Services whose IP addresses will be resolved by OpenShift client. " +
                   "The addresses will be stored in ServiceHelper#ServiceContext. Empty by default.",
             converter = ArgsConverter.class)
   protected List<String> resolveServiceAddresses = Collections.emptyList();

   @ProvidesTrait
   public Lifecycle getLifecycle() {
      return this;
   }

   @ProvidesTrait
   public PodCluster getClustered() {
      return cluster;
   }

   @Init
   public void init() {
      if (oauthToken == null) {
         if (username == null || password == null) {
            throw new RuntimeException("Either oauthToken or both username and password must be defined!");
         }
      }
      configureOpenShiftClient();
      cluster = new PodCluster(this);
   }

   @Override
   public void start() {
      if (templateFile != null) {
         deployTemplate();
      }
      cluster.startMembershipUpdates();
      Map<String, Object> serviceExports = new HashMap<>();
      resolvePodAddresses.stream().forEach(name -> serviceExports.put(name, getPodIP(name)));
      resolveServiceAddresses.stream().forEach(name -> serviceExports.put(name, getServiceHost(name)));
      ServiceHelper.getContext().addProperties(serviceExports);
      started = true;
   }

   private void deployTemplate() {
      URL templateURL;
      try {
         URL resource = getClass().getResource("/" + templateFile);
         Path filesystemFile = FileSystems.getDefault().getPath(templateFile);
         if (resource != null) {
            templateURL = resource;
         } else if (filesystemFile.toFile().exists()) {
            templateURL = filesystemFile.toFile().toURI().toURL();
            log.info("Found " + templateFile + " on file system");
         } else {
            throw new FileNotFoundException("File " + templateFile + " not found neither as resource nor in filesystem.");
         }
         createdObjects = deployTemplate(templateURL);
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   private KubernetesList deployTemplate(URL templateURL) throws IOException {
      List<ParameterValue> paramValuelist = new ArrayList<>();

      //Due to a bug in the Fabric8 client we need to replace the parameters
      //manually before loading the template rather than by calling .withParameters(...)
      try (InputStream stream = replaceAllParams(templateURL.openStream())) {
         TemplateResource<Template, KubernetesList, DoneableTemplate> templateHandle =
            openShiftClient.templates().inNamespace(openShiftClient.getNamespace())
               .load(stream);

         paramValuelist.addAll(params.entrySet().stream()
            .map(entry -> new ParameterValue(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList()));

         KubernetesList resourceList = templateHandle.process(paramValuelist.toArray(new ParameterValue[]{}));
         return openShiftClient.lists().inNamespace(openShiftClient.getNamespace()).create(resourceList);
      }
   }

   private InputStream replaceAllParams(InputStream stream) throws IOException {
      String result = read(stream);
      for (Map.Entry<String, String> e : params.entrySet()) {
         result = result.replaceAll("\\$\\{" + e.getKey() + "}", e.getValue());
      }
      return new ByteArrayInputStream(result.getBytes());
   }

   private static String read(InputStream input) throws IOException {
      try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input))) {
         return buffer.lines().collect(Collectors.joining("\n"));
      }
   }

   private void configureOpenShiftClient() {
      ConfigBuilder builder = new ConfigBuilder();
      builder.withMasterUrl(masterUrl).withNamespace(namespace);
      if (oauthToken != null) {
         builder.withOauthToken(oauthToken);
      } else {
         builder.withUsername(username).withPassword(password);
      }
      openShiftClient = new DefaultOpenShiftClient(builder.build());
   }

   @Override
   public void stop() {
      cluster.stopMembershipUpdates();
      if (cleanup) {
         cleanOpenShiftNamespace();
      }
      started = false;
   }

   public void cleanOpenShiftNamespace() {
      deleteCreatedObjects();
      deletePersistentVolumeClaims();
   }

   private void deleteCreatedObjects() {
      for (HasMetadata metadata : createdObjects.getItems()) {
         log.info(String.format("Deleting %s : %s", metadata.getKind(), metadata.getMetadata().getName()));
         deleteWithRetries(metadata);
      }
   }

   private void deletePersistentVolumeClaims() {
      log.info("Deleting persistent volume claims");
      openShiftClient.persistentVolumeClaims().withGracePeriod(0).delete();
   }

   private void deleteWithRetries(HasMetadata metadata) {
      try {
         TimeService.waitFor(() -> {
               try {
                  //return false on success
                  return !openShiftClient.resource(metadata).withGracePeriod(0).delete();
               } catch (KubernetesClientException e) {
                  log.errorf("Failed to delete resource %s: %s. Retrying... ", metadata.getKind(),
                     metadata.getMetadata().getName());
                  return false; //failed deletion
               }
            }
         );
      } catch (RuntimeException e) {
         log.errorf("Unable to delete resource %s: %s ", metadata.getKind(),
            metadata.getMetadata().getName());
      }
   }

   @Override
   public boolean isRunning() {
      return started;
   }

   public List<Pod> getReadyPods(Map<String, String> labels) {
      return openShiftClient.pods().withLabels(labels).list().getItems().stream()
         .filter(pod -> pod.getStatus().getConditions().stream()
            .filter(condition -> "Ready".equals(condition.getType()))
            .findFirst()
            .map(readyCondition -> "True".equals(readyCondition.getStatus()))
            .orElse(false)
         ).collect(Collectors.toList());
   }

   public String getPodIP(String name) {
      TimeService.waitFor(() -> {
            try {
               Pod pod = openShiftClient.pods().withName(name).get();
               return pod != null && pod.getStatus().getPodIP() != null;
            } catch (KubernetesClientException e) {
               log.errorf("Failed to retrieve pod address. Retrying... ", e);
               return false;
            }
         }
      );
      return openShiftClient.pods().withName(name).get().getStatus().getPodIP();
   }

   public String getServiceHost(String name) {
      ServiceSpec spec = openShiftClient.services().withName(name).get().getSpec();
      if (spec != null) {
         String host;
         if (spec.getExternalName() != null && !spec.getExternalName().isEmpty()) {
            host = spec.getExternalName();
         } else {
            host = spec.getClusterIP();
         }
         return host;
      }
      return null;
   }
}

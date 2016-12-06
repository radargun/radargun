package org.radargun.service;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.DockerCmdExecFactory;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.InternetProtocol;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.netty.NettyDockerCmdExecFactory;
import org.radargun.Service;
import org.radargun.config.Converter;
import org.radargun.config.Destroy;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.EnvsConverter;
import org.radargun.utils.Utils;

/**
 * A service that pulls a Docker image from a remote Docker registry and starts a container based
 * on this image. A user can specify env variables that will be passed to the running container
 * as well as exposed ports from the container.
 * The container runs in "host" network mode and thus it has same network settings as the host. See
 * http://www.dasblinkenlichten.com/docker-networking-101-host-mode/ for more information about
 * host network mode.
 *
 * @author Martin Gencur
 */
@Service(doc = DockerService.SERVICE_DESCRIPTION)
public class DockerService implements Lifecycle {

   protected final Log log = LogFactory.getLog(getClass());
   protected static final String SERVICE_DESCRIPTION = "Docker Service";
   protected static final String DOCKER_HOST_NETWORK_MODE = "host";

   private DockerCmdExecFactory dockerCmdExecFactory;
   private DockerClient dockerClient;
   private String dockerContainerId;
   private boolean started = false;

   @Property(doc = "Uri of docker server. Default is unix:///var/run/docker.sock")
   protected String serverUri = "unix:///var/run/docker.sock";

   @Property(doc = "Location of docker registry. Default is the official Docker registry.")
   protected String dockerRegistry = "registry.hub.docker.com";

   @Property(doc = "Username to be used to connect to Docker registry. Default is an empty value which can be used ONLY when the image is already available in the local Docker registry.")
   protected String username;

   @Property(doc = "Password to be used to connect to Docker registry. Default is an empty value which can be used ONLY when the image is already available in the local Docker registry.")
   protected String password;

   @Property(doc = "Image that will be downloaded from Docker registry and used to start a container", optional = false)
   protected String image;

   @Property(doc = "Name of the started Docker container. Empty by default.", optional = false)
   protected String containerName;

   @Property(doc = "Environment variables that will be passed to Docker container during startup. This is equivalent to starting Docker container with -e parameters. Empty by default.", converter = EnvsConverter.class)
   protected Map<String, String> env = Collections.emptyMap();

   @Property(doc = "The list of ports exposed by the container. This list should mimic ports exposed from a Docker image through EXPOSE. Empty by default.", converter = PortsConverter.class)
   protected List<ExposedPort> exposedPorts = Collections.emptyList();

   @ProvidesTrait
   public Lifecycle getLifecycle() {
      return this;
   }

   @Override
   public void start() {
      configureDockerClient();

      String imageName = dockerRegistry + "/" + image;
      CreateContainerCmd createCmd = dockerClient.createContainerCmd(imageName)
         .withName(containerName)
         .withNetworkMode(DOCKER_HOST_NETWORK_MODE)
         .withExposedPorts(exposedPorts)
         .withPublishAllPorts(true)
         .withEnv(env.entrySet().stream().map(e -> new String(e.getKey() + "=" + e.getValue())).collect(Collectors.toList()));

      try {
         dockerContainerId = createCmd.exec().getId();
      } catch (NotFoundException e) {
         log.warnf("Docker Image %s is not on localhost and it is going to be automatically pulled.", imageName);
         pullImage(imageName);
         dockerContainerId = createCmd.exec().getId();
      } catch (ConflictException e) {
         log.warnf("Container name %s is already in use. Container is going to be removed.", containerName);
         stopAndRemoveContainer(containerName);
         dockerContainerId = createCmd.exec().getId();
      }
      log.infof("Created container %s with id %s", containerName, dockerContainerId);

      dockerClient.startContainerCmd(dockerContainerId).exec();
      started = true;
      log.infof("Started container with id %s", dockerContainerId);
      printContainerLog(dockerContainerId);
   }

   private void configureDockerClient() {
      dockerCmdExecFactory = new NettyDockerCmdExecFactory();

      DefaultDockerClientConfig.Builder configBuilder = DefaultDockerClientConfig
         .createDefaultConfigBuilder();

      DockerClientConfig dockerClientConfig = configBuilder
         .withDockerHost(serverUri)
         .withRegistryUrl(dockerRegistry)
         .withRegistryUsername(username)
         .withRegistryPassword(password)
         .build();

      dockerClient = DockerClientBuilder.getInstance(dockerClientConfig)
         .withDockerCmdExecFactory(dockerCmdExecFactory)
         .build();
   }

   private void stopAndRemoveContainer(String container) {
      try {
         dockerClient.stopContainerCmd(container).exec();
      } catch (NotModifiedException e1) {
         // Container was already stopped
      }
      log.warnf("Removing container %s", container);
      dockerClient.removeContainerCmd(container).exec();
   }

   private void pullImage(String imageName) {
      PullImageCmd pullImageCmd = this.dockerClient.pullImageCmd(imageName);
      pullImageCmd.exec(new PullImageResultCallback(){
         boolean success = false;

         public void onNext(PullResponseItem item) {
            success = success | item.isPullSuccessIndicated();
            log.trace(item.toString());
         }
         public void awaitSuccess() {
            try {
               this.awaitCompletion();
            } catch (InterruptedException var2) {
               throw new DockerClientException("", var2);
            }
            if(!success) {
               throw new DockerClientException("Could not pull image");
            }
         }
      }).awaitSuccess();
   }

   private void printContainerLog(String containerId) {
      LogCallback loggingCallback = new LogCallback();

      dockerClient.logContainerCmd(containerId)
         .withStdErr(true)
         .withStdOut(true)
         .withFollowStream(true)
         .withTailAll()
         .exec(loggingCallback);

      try {
         loggingCallback.awaitCompletion(3, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         log.error(e.getMessage());
         Thread.currentThread().interrupt();
      }
   }

   private class LogCallback extends LogContainerResultCallback {
      @Override
      public void onNext(Frame frame) {
         log.info(frame.toString());
      }
   }

   @Override
   public void stop() {
      dockerClient.stopContainerCmd(dockerContainerId).exec();
      started = false;
      log.infof("Stopped container with id %s", dockerContainerId);
   }

   @Destroy
   public void destroy() {
      Utils.close(dockerCmdExecFactory, dockerClient);
   }

   @Override
   public boolean isRunning() {
      return started;
   }

   /**
    * Converts a series of "port/(udp|tcp)" definitions to a list of ExposedPort
    */
   private static class PortsConverter implements Converter<List<ExposedPort>> {
      @Override
      public List<ExposedPort> convert(String string, Type type) {
         List<ExposedPort> ports = new ArrayList<>();
         String[] portSlashProtocolList = string.trim().split("\n");
         for (String portSlashProtocol : portSlashProtocolList) {
            if (portSlashProtocol.split("/").length != 2)
               throw new IllegalArgumentException("The exposed port must be specified in this format: port/(udp|tcp)");
            int port = Integer.parseInt(portSlashProtocol.trim().split("/")[0]);
            InternetProtocol protocol = InternetProtocol.parse(portSlashProtocol.trim().split("/")[1]);
            ports.add(new ExposedPort(port, protocol));
         }
         return ports;
      }

      @Override
      public String convertToString(List<ExposedPort> ports) {
         StringBuilder sb = new StringBuilder();
         for (ExposedPort port : ports) {
            sb.append(port.getPort()).append("/").append(port.getProtocol().toString()).append('\n');
         }
         return sb.toString();
      }

      @Override
      public String allowedPattern(Type type) {
         return "\\s*([0-9]+/(tcp|udp)\\s*)+"; //matches one or more occurrences of "port/(tcp|udp)"
      }
   }
}

package org.radargun.service;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
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
import org.radargun.ServiceHelper;
import org.radargun.config.Converter;
import org.radargun.config.Destroy;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.ArgsConverter;
import org.radargun.utils.EnvsConverter;
import org.radargun.utils.TimeConverter;
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

   private DockerCmdExecFactory dockerCmdExecFactory;
   private DockerClient dockerClient;
   private String dockerContainerId;
   private boolean started = false;

   @Property(doc = "Uri of docker server. Default is unix:///var/run/docker.sock")
   protected String serverUri = "unix:///var/run/docker.sock";

   @Property(doc = "Location of docker registry. Default is empty which means that the image is expected to exist on localhost. Use registry.hub.docker.com when the image is available in the main Docker repository.")
   protected String dockerRegistry;

   @Property(doc = "Username to be used to connect to Docker registry. Default is an empty value which can be used ONLY when the image is already available in the local Docker registry.")
   protected String username;

   @Property(doc = "Password to be used to connect to Docker registry. Default is an empty value which can be used ONLY when the image is already available in the local Docker registry.")
   protected String password;

   @Property(doc = "Image that will be downloaded from Docker registry and used to start a container", optional = false)
   protected String image;

   @Property(doc = "Name of the started Docker container. Defaults to ${group.name}-${slave.id}.")
   protected String containerName;

   @Property(doc = "Environment variables that will be passed to Docker container during startup. This is equivalent to starting Docker container with -e parameters. Empty by default.", converter = EnvsConverter.class)
   protected Map<String, String> env = Collections.emptyMap();

   @Property(doc = "Set alternative entrypoint. By default not set.", converter = ArgsConverter.class)
   protected List<String> entrypoint = null;

   @Property(doc = "Arguments passed to the container during startup. Empty by default.", converter = ArgsConverter.class)
   protected List<String> cmd = Collections.emptyList();

   @Property(doc = "The list of ports exposed by the container. This list should mimic ports exposed from a Docker image through EXPOSE. Empty by default.", converter = PortsConverter.class)
   protected List<ExposedPort> exposedPorts = Collections.emptyList();

   @Property(doc = "Network mode for the container. Common values are 'bridge', 'host', 'container:<name|id>' or network name/id. Not set by default.")
   protected String network = null;

   @Property(doc = "Block starting method until we read a message from the log matching regexp provided here. Not waiting by default")
   protected String awaitLog;

   @Property(doc = "Timeout for waiting for a log (see 'await-log'). By default 1 minute.", converter = TimeConverter.class)
   protected long awaitLogTimeout = 60000;

   @ProvidesTrait
   public Lifecycle getLifecycle() {
      return this;
   }

   @Override
   public void start() {
      configureDockerClient();

      String imageName;
      if (dockerRegistry == null || dockerRegistry.isEmpty()) {
         imageName = image;
      } else {
         imageName = dockerRegistry + "/" + image;
      }
      String containerName = this.containerName;
      if (containerName == null) {
         int slaveIndex = ServiceHelper.getSlaveIndex();
         containerName = ServiceHelper.getCluster().getGroup(slaveIndex).name + "-" + slaveIndex;
      }
      CreateContainerCmd createCmd = dockerClient.createContainerCmd(imageName)
         .withName(containerName)
         .withExposedPorts(exposedPorts)
         .withPublishAllPorts(true)
         .withEnv(env.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.toList()))
         .withCmd(cmd);
      if (network != null) {
         createCmd = createCmd.withNetworkMode(network);
      }
      if (entrypoint != null) {
         createCmd = createCmd.withEntrypoint(entrypoint);
      }

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
            } catch (InterruptedException ie) {
               log.error(ie.getMessage());
               Thread.currentThread().interrupt();
            }
            if(!success) {
               throw new DockerClientException("Could not pull image");
            }
         }
      }).awaitSuccess();
   }

   private void printContainerLog(String containerId) {
      CountDownLatch logLatch = null;
      Pattern pattern = null;
      if (awaitLog != null) {
         logLatch = new CountDownLatch(1);
         pattern = Pattern.compile(awaitLog);
      }
      LogCallback loggingCallback = new LogCallback(logLatch, pattern);

      dockerClient.logContainerCmd(containerId)
         .withStdErr(true)
         .withStdOut(true)
         .withFollowStream(true)
         .withTailAll()
         .exec(loggingCallback);

      try {
         if (!logLatch.await(awaitLogTimeout, TimeUnit.MILLISECONDS)) {
            throw new RuntimeException("Timed out reading log");
         }
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new RuntimeException("Interrupted reading log", e);
      }
   }

   private class LogCallback extends LogContainerResultCallback {
      private CountDownLatch latch;
      private final Pattern pattern;
      private ByteBuffer buffer = ByteBuffer.allocate(1024);

      public LogCallback(CountDownLatch latch, Pattern pattern) {
         this.latch = latch;
         this.pattern = pattern;
      }

      @Override
      public void onNext(Frame frame) {
         byte[] payload = frame.getPayload();
         // Remove color escape sequences
         if (buffer.capacity() < payload.length) {
            buffer = ByteBuffer.allocate(Math.max(payload.length, buffer.capacity() * 2));
         } else {
            buffer.rewind();
         }
         for (int i = 0; i < payload.length; i++) {
            byte b = payload[i];
            if (b == 27) { // ESCAPE
               if (++i < payload.length && (b = payload[i]) == '[') {
                  while (++i < payload.length) {
                     b = payload[i];
                     if (b >= '@' && b <= '~') break;
                  }
                  continue;
               } else {
                  buffer.put((byte) 27);
               }
            }
            buffer.put(b);
         }
         int originalPosition = buffer.position();
         // remove trailing newline
         while (buffer.position() > 0) {
            int pos = buffer.position() - 1;
            byte b = buffer.get(pos);
            if (b == '\n' || b == '\r') {
               buffer.position(pos);
            } else break;
         }
         if (latch != null) {
            String message = new String(buffer.array(), 0, buffer.position());
            if (pattern.matcher(message).matches()) {
               latch.countDown();
               latch = null;
            }
         }
         switch (frame.getStreamType()) {
            case STDOUT:
            case RAW:
               System.out.write(buffer.array(), 0, originalPosition);
               break;
            case STDERR:
            default:
               System.err.write(buffer.array(), 0, originalPosition);
         }
      }
   }

   @Override
   public void stop() {
      dockerClient.stopContainerCmd(dockerContainerId).exec();
      try {
         dockerCmdExecFactory.close();
      } catch (IOException e) {
         log.error("Failed to close command exec factory", e);
      }
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

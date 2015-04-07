package org.radargun.utils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.radargun.Directories;
import org.radargun.Slave;

/**
 * This utility will start another process after certain lock (args[0]) is released.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class RestartHelper {
    public static void init() {
        if (ArgsHolder.getTempConfigDir() != null) {
            Utils.deleteOnExitRecursive(new File(ArgsHolder.getTempConfigDir()));
        }
    }

    public static void spawnSlave(int slaveIndex, UUID nextUuid, String plugin) throws IOException {
        // TODO: with VM restarts, we can add VM args/ENV vars to the configuration
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.inheritIO();
        StringBuilder classpathBuilder = new StringBuilder();
        // TODO: add this through plugin configuration
        classpathBuilder.append(Paths.get(System.getProperty("java.home"), "lib", "tools.jar").toString());
        // plugin-specific stuff should be prepended
        ArgsHolder.PluginParam pluginParam = ArgsHolder.getPluginParams().get(plugin);
        Path tempConfigDir = null;
        if (pluginParam != null) {
            if (pluginParam.getPath() != null) {
                File extDir = new File(pluginParam.getPath());
                if (extDir.exists() && extDir.isDirectory()) {
                    addConfAndLib(classpathBuilder, extDir);
                }
            }
            if (!pluginParam.getConfigFiles().isEmpty()) {
                tempConfigDir = Files.createTempDirectory("radargun-" + plugin + "-" + slaveIndex);
                classpathBuilder.append(File.pathSeparatorChar).append(tempConfigDir.toAbsolutePath());
                for (String configFile : pluginParam.getConfigFiles()) {
                    File file = new File(configFile);
                    Files.copy(file.toPath(), tempConfigDir.resolve(file.getName()));
                }
            }
        }
        File pluginDir = new File(Directories.PLUGINS_DIR, plugin);
        if (pluginDir.exists() && pluginDir.isDirectory()) {
            addConfAndLib(classpathBuilder, pluginDir);
        }
        addConfAndLib(classpathBuilder, Directories.ROOT_DIR);

        ListBuilder<String> command = new ListBuilder<>(new ArrayList<String>());
        String javaExecutable = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        // we need to run intermediate process that waits until this process ends
        command.add(javaExecutable)
              .add("-cp").add(Directories.LIB_DIR.toString() + "/*").add(RestartHelper.class.getName())
              .add(createLockedTempFile(slaveIndex));
        command.add(javaExecutable);
        List<String> inputArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
        command.addAll(inputArguments);
        // we have to specify log4j configuration explicitly because plugin can define its default config
        if (!Projections.any(inputArguments, new Projections.Condition<String>() {
            @Override
            public boolean accept(String s) {
                return s.startsWith("-Dlog4j.configuration");
            }
        })) {
            command.add("-Dlog4j.configuration=file://" + Directories.ROOT_DIR + "/conf/log4j.xml");
        }
        command.add("-cp").add(classpathBuilder.toString());
        command.add(Slave.class.getName());
        command.add(ArgsHolder.MASTER).add(ArgsHolder.getMasterHost() + ":" + ArgsHolder.getMasterPort());
        command.add(ArgsHolder.SLAVE_INDEX).add(String.valueOf(slaveIndex));
        command.add(ArgsHolder.UUID).add(nextUuid.toString());
        command.add(ArgsHolder.CURRENT_PLUGIN).add(plugin);
        if (tempConfigDir != null) {
            command.add(ArgsHolder.TEMP_CONFIG_DIR).add(tempConfigDir.toString());
        }
        // we have to repeat the args for future generations
        for (Map.Entry<String, ArgsHolder.PluginParam> entry : ArgsHolder.getPluginParams().entrySet()) {
            command.add(ArgsHolder.ADD_PLUGIN).add(entry.getValue().getPath());
            for (String configFile : entry.getValue().getConfigFiles()) {
                command.add(ArgsHolder.ADD_CONFIG).add(entry.getKey() + ":" + configFile);
            }
        }
        processBuilder.command(command.build());
        processBuilder.start();
    }

    private static String createLockedTempFile(int slaveIndex) throws IOException {
        File tempFile = File.createTempFile("restart-" + slaveIndex, ".tmp");
        new RandomAccessFile(tempFile, "rw").getChannel().lock();
        return tempFile.getAbsolutePath();
    }

    private static void addConfAndLib(StringBuilder classpathBuilder, File parentDir) {
        classpathBuilder.append(File.pathSeparatorChar).append(parentDir).append(File.separatorChar).append("conf/");
        for (File f : new File(parentDir, "lib").listFiles()) {
            classpathBuilder.append(File.pathSeparatorChar);
            classpathBuilder.append(f.getAbsolutePath());
        }
    }

    public static void main(String[] args) throws IOException {
        File file = new File(args[0]);
        System.out.printf("%s: Waiting for lock on file %s%n",
              new SimpleDateFormat("HH:mm:ss,S").format(new Date()), args[0]);
        FileLock lock = new RandomAccessFile(file, "rw").getChannel().lock();
        System.out.printf("%s: Lock acquired%n",
              new SimpleDateFormat("HH:mm:ss,S").format(new Date()));
        lock.release();
        file.delete();

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.inheritIO();
        ArrayList<String> command = new ArrayList<>();
        for (int i = 1; i < args.length; ++i) command.add(args[i]);
        processBuilder.command(command);
        processBuilder.start();
        System.exit(0);
    }
}

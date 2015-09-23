package org.radargun.logging;

import java.io.File;
import java.io.Serializable;

import org.apache.logging.log4j.core.ErrorHandler;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LifeCycle;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.RollingRandomAccessFileAppender;
import org.apache.logging.log4j.core.appender.rolling.RolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

/**
 * Apends an node instance identifier at the end of the filename.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Plugin(name = "PerNodeRollingRandomAccessFile", category = "Core", elementType = "appender", printObject = true)
public final class PerNodeRollingRandomAccessFileAppender implements org.apache.logging.log4j.core.Appender {

   public static final String PROP_NAME = "log4j.file.prefix";
   private final RollingRandomAccessFileAppender appender;

   private PerNodeRollingRandomAccessFileAppender(RollingRandomAccessFileAppender appender) {
      this.appender = appender;
   }

   @PluginFactory
   public static PerNodeRollingRandomAccessFileAppender createAppender(
         @PluginAttribute("fileName") final String fileName,
         @PluginAttribute("filePattern") final String filePattern,
         @PluginAttribute("append") final String append,
         @PluginAttribute("name") final String name,
         @PluginAttribute("immediateFlush") final String immediateFlush,
         @PluginAttribute("bufferSizeStr") final String bufferSizeStr,
         @PluginElement("Policy") final TriggeringPolicy policy,
         @PluginElement("Strategy") RolloverStrategy strategy,
         @PluginElement("Layout") Layout<? extends Serializable> layout,
         @PluginElement("Filter") final Filter filter,
         @PluginAttribute("ignoreExceptions") final String ignore,
         @PluginAttribute("advertise") final String advertise,
         @PluginAttribute("advertiseURI") final String advertiseURI,
         @PluginConfiguration final Configuration config) {
      RollingRandomAccessFileAppender appender = RollingRandomAccessFileAppender.createAppender(
            appendNodeIndex(fileName), appendNodeIndex(filePattern), append, name, immediateFlush, bufferSizeStr, policy,
            strategy, layout, filter, ignore, advertise, advertiseURI, config);
      return new PerNodeRollingRandomAccessFileAppender(appender);
   }

   private static String appendNodeIndex(String s) {
      String prop = System.getProperty(PROP_NAME);
      if (prop != null) {
         System.out.println("PerNodeRollingFileAppender::Using file prefix:" + prop);
         int lastIndex = s.lastIndexOf(File.separator);
         if (lastIndex < 0) {
            return prop + "_" + s;
         } else {
            return s.substring(0, lastIndex + 1) + prop + s.substring(lastIndex + 1);
         }
      } else {
         System.out.println("PerNodeRollingFileAppender::Not using file prefix.");
         return s;
      }
   }


   @Override
   public void append(LogEvent event) {
      appender.append(event);
   }

   @Override
   public String getName() {
      return appender.getName();
   }

   @Override
   public Layout<? extends Serializable> getLayout() {
      return appender.getLayout();
   }

   @Override
   public boolean ignoreExceptions() {
      return appender.ignoreExceptions();
   }

   @Override
   public ErrorHandler getHandler() {
      return appender.getHandler();
   }

   @Override
   public void setHandler(ErrorHandler handler) {
      appender.setHandler(handler);
   }

   @Override
   public State getState() {
      return appender.getState();
   }

   @Override
   public void start() {
      appender.start();
   }

   @Override
   public void stop() {
      appender.stop();
   }

   @Override
   public boolean isStarted() {
      return appender.isStarted();
   }

   @Override
   public boolean isStopped() {
      return appender.isStopped();
   }
}

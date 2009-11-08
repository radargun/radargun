package org.cachebench.fwk.config;

import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;


/**
 * A configuration bean to run the benchmark framework in "local" mode.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "")
@XmlRootElement(name = "local")
public class LocalModeConfig {

   @XmlAttribute(required = true, name = "plugin-config")
   @XmlJavaTypeAdapter(SystemPropertyAwareStringUnmarshaller.class)
   protected String pluginConfig;

   @XmlAttribute(required = true)
   @XmlJavaTypeAdapter(SystemPropertyAwareIntegerUnmarshaller.class)
   @XmlSchemaType(name = "integer")
   protected Integer threads;

   @XmlAttribute(required = true)
   @XmlJavaTypeAdapter(SystemPropertyAwareIntegerUnmarshaller.class)
   @XmlSchemaType(name = "integer")
   protected Integer operations;

   @XmlAttribute (required = true, name="write-ratio")
   @XmlJavaTypeAdapter(SystemPropertyAwareFloatUnmarshaller.class)
   @XmlSchemaType(name = "float")
   protected Float writeRatio;

   public String getPluginConfig() {
      return pluginConfig;
   }

   public void setPluginConfig(String pluginConfig) {
      this.pluginConfig = pluginConfig;
   }

   public Integer getThreads() {
      return threads;
   }

   public void setThreads(Integer threads) {
      this.threads = threads;
   }

   public Integer getOperations() {
      return operations;
   }

   public void setOperations(Integer operations) {
      this.operations = operations;
   }

   public Float getWriteRatio() {
      return writeRatio;
   }

   public void setWriteRatio(Float writeRatio) {
      this.writeRatio = writeRatio;
   }
}

package org.cachebench.fwk.config;

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class SystemPropertyAwareFloatUnmarshaller extends XmlAdapter<String, Float> {

   public Float unmarshal(String value) {
      return ConfigHelper.parseFloat(value);
   }

   public String marshal(Float value) {
      if (value == null) return null;
      return value.toString();
   }
}
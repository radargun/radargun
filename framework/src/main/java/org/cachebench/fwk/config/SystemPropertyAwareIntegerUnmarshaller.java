package org.cachebench.fwk.config;

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class SystemPropertyAwareIntegerUnmarshaller extends XmlAdapter<String, Integer> {
   public Integer unmarshal(String value) {
      return ConfigHelper.parseInt(value);
   }

   public String marshal(Integer value) {
      if (value == null) return null;
      return value.toString();
   }
}

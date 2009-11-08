package org.cachebench.fwk.config;

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class SystemPropertyAwareStringUnmarshaller extends XmlAdapter<String, String> {
   public String unmarshal(String value) {
      return ConfigHelper.parseString(value);
   }

   public String marshal(String value) {
      if (value == null) return null;
      return value.toString();
   }
}

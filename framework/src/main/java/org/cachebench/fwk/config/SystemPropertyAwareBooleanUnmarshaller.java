package org.cachebench.fwk.config;

import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.annotation.adapters.XmlAdapter;

public class SystemPropertyAwareBooleanUnmarshaller
      extends XmlAdapter<String, Boolean> {

   public Boolean unmarshal(String value) {
      return (ConfigHelper.parseBoolean(value));
   }

   public String marshal(Boolean value) {
      if (value == null) return null;
      return DatatypeConverter.printBoolean(value);
   }
}

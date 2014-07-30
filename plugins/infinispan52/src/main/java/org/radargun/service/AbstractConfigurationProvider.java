package org.radargun.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.ConfigurationProvider;

/**
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
public abstract class AbstractConfigurationProvider implements ConfigurationProvider {

   protected final Log log = LogFactory.getLog(getClass());

   protected void loadConfigFile(String filename, Map<String, byte[]> configs) {
      if (filename != null) {
         try {
            InputStream is = getClass().getClassLoader().getResourceAsStream(filename);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            int length;
            byte[] content = new byte[1024];
            while ((length = is.read(content, 0, content.length)) != -1) {
               bos.write(content, 0, length);
            }
            configs.put(filename, bos.toByteArray());
         } catch (Exception e) {
            log.error("Error while reading configuration file (" + filename + ")", e);
         }
      }
   }
}

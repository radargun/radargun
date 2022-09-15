package org.radargun.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.radargun.utils.Utils;

/**
 * @author Vojtech Juranek &lt;vjuranek@redhat.com&gt;
 */
public class JDG66HotrodQueryable extends InfinispanHotrodQueryable {

   public JDG66HotrodQueryable(Infinispan60HotrodService service) {
      super(service);
   }

   @Override
   protected void registerProtofilesLocal(SerializationContext context) {
      try {
         context.registerProtoFiles((new FileDescriptorSource()).addProtoFiles(service.protofiles));
      } catch (Exception e) {
         throw new IllegalArgumentException("Failed to read protofiles " + Arrays.toString(service.protofiles), e);
      }
   }

   @Override
   protected void doJmxRegistration(MBeanServerConnection connection) {
      ObjectName objName = null;
      try {
         objName = new ObjectName(service.jmxDomain + ":type=RemoteQuery,name=" + ObjectName.quote(service.clusterName)
               + ",component=" + PROTOBUF_COMPONENT_NAME);
      } catch (MalformedObjectNameException e) {
         throw new IllegalStateException("Failed to register protofiles", e);
      }

      for (String protofile : service.protofiles) {
         byte[] descriptor;
         try (InputStream inputStream = getClass().getResourceAsStream(protofile)) {
            descriptor = Utils.readAsBytes(inputStream);
         } catch (IOException e) {
            throw new IllegalStateException("Failed to read protofile " + protofile, e);
         }
         try {
            connection.invoke(objName, "registerProtofile", new Object[] { protofile, new String(descriptor) },
                  new String[] { String.class.getName(), String.class.getName() });
            log.info("Protofile " + protofile + " registered.");
         } catch (Exception e) {
            throw new IllegalStateException("Failed to register protofile " + protofile, e);
         }
      }
   }
}

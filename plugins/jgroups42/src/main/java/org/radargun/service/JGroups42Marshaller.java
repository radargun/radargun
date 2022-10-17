package org.radargun.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class JGroups42Marshaller {

   public byte[] toByteArray(Object key, Object value) {
      try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
           ObjectOutputStream oos = new ObjectOutputStream(bos)) {
         oos.writeObject(key);
         oos.writeObject(value);
         return bos.toByteArray();
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   public Object toObject(byte[] bytes) {
      try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
           ObjectInputStream ois = new ObjectInputStream(bis)) {
         return ois.readObject();
      } catch (ClassNotFoundException e) {
         throw new RuntimeException(e);
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }
}

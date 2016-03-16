package org.radargun.stages.cache.generators;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.radargun.config.DefinitionElement;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * @author Matej Cimbora
 */
@DefinitionElement(name = "composed", doc = "Creates composed values (containing numeric and text values).")
public class ComposedObjectGenerator implements ValueGenerator {

   private static Log log = LogFactory.getLog(ComposedObjectGenerator.class);

   @Property(doc = "Text generator.", complexConverter = ValueGenerator.ComplexConverter.class, optional = false)
   private SingleWordGenerator singleWordGenerator;
   @Property(doc = "Number generator.", complexConverter = ValueGenerator.ComplexConverter.class, optional = false)
   private NumberObjectGenerator numberObjectGenerator;
   @Property(doc = "Size of nested collection. Default is 3. If the value is smaller than 0, collection reference remains null.")
   private int nestedCollectionSize = 3;
   @Property(name = "class", doc = "Class instantiated by this generator. Default is 'org.radargun.query.ComposedObject'.")
   private String clazzName = "org.radargun.query.ComposedObject";
   @Property(name = "textClass", doc = "Text object class instantiated by this generator. Default is 'org.radargun.query.TextObject'.")
   private String textObjectClassName = "org.radargun.query.TextObject";
   @Property(name = "numberClass", doc = "Number object class instantiated by this generator. Default is 'org.radargun.query.NumberObject'.")
   private String numberObjectClassName = "org.radargun.query.NumberObject";

   private Class<?> clazz;
   private Class<?> textObjectClass;
   private Class<?> numberObjectClass;
   private Constructor<?> ctor;

   @Init
   public void init() {
      try {
         ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
         textObjectClass = classLoader.loadClass(textObjectClassName);
         numberObjectClass = classLoader.loadClass(numberObjectClassName);
         clazz = classLoader.loadClass(clazzName);
         ctor = clazz.getConstructor(textObjectClass, numberObjectClass, List.class, List.class);
      } catch (Exception e) {
         // trace as this can happen on master node
         log.tracef(e, "Could not initialize generator %s", this);
      }
   }


   @Override
   public Object generateValue(Object key, int size, Random random) {
      Object textObject = singleWordGenerator.generateValue(key, size, random);
      Object numberObject = numberObjectGenerator.generateValue(key, size, random);
      List textObjectList = null;
      List numberObjectList = null;
      if (nestedCollectionSize >= 0) {
         textObjectList = new ArrayList(nestedCollectionSize);
         numberObjectList = new ArrayList(nestedCollectionSize);
         for (int i = 0; i < nestedCollectionSize; i++) {
            try {
               textObjectList.add(singleWordGenerator.generateValue(key, size, random));
               numberObjectList.add(numberObjectGenerator.generateValue(key, size, random));
            } catch (Exception e) {
               log.error("Failed to generate value", e);
            }
         }
      }
      return newInstance(textObject, numberObject, textObjectList, numberObjectList);
   }

   @Override
   public int sizeOf(Object value) {
      // FIXME
      return -1;
   }

   @Override
   public boolean checkValue(Object value, Object key, int expectedSize) {
      // FIXME
      return value.getClass().equals(clazz);
   }

   private Object newInstance(Object textObject, Object numberObject, List<?> textCollection, List<?> numberCollection) {
      if (ctor == null) throw new IllegalStateException("The generator was not properly initialized");
      try {
         return ctor.newInstance(textObject, numberObject, textCollection, numberCollection);
      } catch (Exception e) {
         return new IllegalStateException(e);
      }
   }
}

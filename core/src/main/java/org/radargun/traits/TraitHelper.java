package org.radargun.traits;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Helper class for dealing with traits.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class TraitHelper {
   public enum InjectResult {
      SUCCESS,
      FAILURE,
      SKIP
   }

   /**
    * Via reflection, inject the provided traits into field on the target
    * annotated by {@link InjectTrait @InjectTrait}.
    *
    * @param target
    * @param traits
    * @return
    *    SUCCESS if all @InjectTrait requests have been satisfied
    *    FAILURE if a MANDATORY InjectTrait could not be injected (no such trait was provided)
    *    SKIP if some trait with SKIP InjectTrait could not be injected
    */
   public static InjectResult inject(Object target, Map<Class<?>, Object> traits) {
      Class<?> targetClass = target.getClass();
      InjectResult result = InjectResult.SUCCESS;
      while (targetClass != null) {
         for (Field f : targetClass.getDeclaredFields()) {
            InjectTrait annotation = f.getAnnotation(InjectTrait.class);
            if (annotation == null) continue;
            if (f.getType().getAnnotation(Trait.class) == null) {
               throw new IllegalArgumentException("Field " + f + " wants a trait to be injected but its type is not a trait.");
            }
            Object traitImpl = traits == null ? null : traits.get(f.getType());
            if (traitImpl != null) {
               f.setAccessible(true);
               try {
                  f.set(target, traitImpl);
               } catch (IllegalAccessException e) {
                  throw new RuntimeException("Cannot set trait to field " + f, e);
               }
            } else {
               switch (annotation.dependency()) {
                  case OPTIONAL: break; // no action, leave to null
                  case MANDATORY: return InjectResult.FAILURE;
                  case SKIP: result = InjectResult.SKIP; break;
               }
            }
         }
         targetClass = targetClass.getSuperclass();
      }
      return result;
   }

   /**
    * Inspect all target's public no-arg methods (including those from superclasses)
    * annotated by {@link ProvidesTrait @ProvidesTrait} and call them. The return value
    * is inspected for all implemented interfaces annotated with {@link Trait @Trait}
    * and the return values are added to the result map with these interfaces as keys.
    * @param target
    * @return map of trait classes to objects implementing these traits
    */
   public static Map<Class<?>, Object> retrieve(Object target) {
      Class<?> targetClass = target.getClass();
      Map<Class<?>, Object> traitMap = new HashMap<Class<?>, Object>();
      for (Method m : targetClass.getMethods()) {
         if (m.isBridge()) continue;
         if (m.getAnnotation(ProvidesTrait.class) == null) continue;
         if (m.getParameterTypes().length != 0) {
            throw new IllegalArgumentException("Method " + m + " declares that it provides trait, but it requires parameters.");
         }
         Object traitImpl;
         try {
            traitImpl = m.invoke(target);
         } catch (Exception e) {
            throw new RuntimeException("Error retrieving traits from method " + m, e);
         }
         if (traitImpl == null) {
            continue;
         }
         Set<Class<?>> traits = new HashSet<Class<?>>();
         addAllTraits(traits, traitImpl.getClass());
         for (Class<?> trait : traits) {
            Object prevTraitImpl = traitMap.get(trait);
            if (prevTraitImpl != null && prevTraitImpl != traitImpl) {
               throw new RuntimeException("Trait " + trait.getSimpleName() + " is implemented both by " + prevTraitImpl + " and " + traitImpl);
            }
            traitMap.put(trait, traitImpl);
         }
      }
      return traitMap;
   }

   private static void addAllTraits(Set<Class<?>> traits, Class<?> clazz) {
      if (clazz == null) return;
      if (clazz.getAnnotation(Trait.class) != null) traits.add(clazz);
      addAllTraits(traits, clazz.getSuperclass());
      for (Class<?> iface : clazz.getInterfaces()) {
         addAllTraits(traits, iface);
      }
   }
}

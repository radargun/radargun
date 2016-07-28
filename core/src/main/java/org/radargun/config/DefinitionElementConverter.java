package org.radargun.config;

import java.util.Collection;

/**
 * Complex converter based on {@link DefinitionElement}
 */
public interface DefinitionElementConverter<T> extends ComplexConverter<T> {
   /**
    * @return Classes that could be the conversion result
    */
   Collection<Class<?>> content();

   /**
    * @return Minimal number of attributes that the definition should contain.
    */
   int minAttributes();

   /**
    * @return Maximal number of attributes that the definition should contain. -1 means unlimited.
    */
   int maxAttributes();
}

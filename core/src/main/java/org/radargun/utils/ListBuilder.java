package org.radargun.utils;

import java.util.Collection;
import java.util.List;

/**
 * Simple class to allow fluent API on list
 */
public class ListBuilder<T> {
   private List<T> list;

   public ListBuilder(List<T> list) {
      this.list = list;
   }

   public ListBuilder<T> add(T element) {
      list.add(element);
      return this;
   }

   public ListBuilder<T> addAll(Collection<T> elements) {
      list.addAll(elements);
      return this;
   }

   public List<T> build() {
      return list;
   }
}

package org.radargun.utils;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Helper class for creating projections.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Projections {
   public static <A, B> Collection project(Collection<A> collection, Func<A, B> func) {
      return new ProjectCollection<A, B>(collection, func);
   }

   public static <A> Collection<A> subset(Collection<A> collection, int skip, int limit) {
      return new SubsetCollection<A>(collection, skip, limit);
   }

   public interface Func<A, B> {
      B project(A a);
   }

   private static class ProjectCollection<A, B> implements Collection<B> {
      private final Collection<A> collection;
      private final Func<A, B> func;

      private ProjectCollection(Collection<A> collection, Func<A, B> func) {
         this.collection = collection;
         this.func = func;
      }

      @Override
      public int size() {
         return collection.size();
      }

      @Override
      public boolean isEmpty() {
         return collection.isEmpty();
      }

      @Override
      public boolean contains(Object o) {
         for (A a : collection) {
            B b = func.project(a);
            if ((o == null && b == null) || (b != null && b.equals(o))) return true;
         }
         return false;
      }

      @Override
      public Iterator<B> iterator() {
         return new Iterator<B>() {
            Iterator<A> ait = collection.iterator();
            @Override
            public boolean hasNext() {
               return ait.hasNext();
            }

            @Override
            public B next() {
               return func.project(ait.next());
            }

            @Override
            public void remove() {
               throw new UnsupportedOperationException();
            }
         };
      }

      @Override
      public Object[] toArray() {
         Object[] array = new Object[collection.size()];
         int i = 0;
         for (A a : collection) {
            array[i++] = func.project(a);
         }
         return array;
      }

      @Override
      public boolean add(Object o) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean remove(Object o) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean containsAll(Collection<?> c) {
         Set set = new HashSet(c);
         for (A a : collection) {
            B b = func.project(a);
            if (set.contains(b)) set.remove(b);
            if (set.isEmpty()) return true;
         }
         return set.isEmpty();
      }

      @Override
      public boolean removeAll(Collection<?> c) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean addAll(Collection c) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean retainAll(Collection<?> c) {
         throw new UnsupportedOperationException();
      }

      @Override
      public void clear() {
         throw new UnsupportedOperationException();
      }

      @Override
      public <T> T[] toArray(T[] array) {
         if (array.length < collection.size()) {
            array = (T[]) Array.newInstance(array.getClass().getComponentType(), collection.size());
         }
         int i = 0;
         for (A a : collection) {
            array[i++] = (T) func.project(a);
         }
         return array;
      }
   }

   private static class SubsetCollection<A> implements Collection<A> {
      private final Collection<A> collection;
      private final int skip;
      private final int limit;

      private SubsetCollection(Collection<A> collection, int skip, int limit) {
         this.collection = collection;
         this.skip = skip;
         this.limit = limit;
      }

      @Override
      public int size() {
         return Math.min(Math.max(collection.size() - skip, 0), limit);
      }

      @Override
      public boolean isEmpty() {
         return limit == 0 || collection.size() - skip <= 0;
      }

      @Override
      public boolean contains(Object o) {
         int skipped = 0, count = 0;
         for (A a : collection) {
            if (skipped++ < skip) continue;
            if (count++ >= limit) break;
            if ((a == null && o == null) || (a != null && a.equals(o))) return true;
         }
         return false;
      }

      @Override
      public Iterator<A> iterator() {
         final Iterator<A> it = collection.iterator();
         for (int i = 0; it.hasNext() && i < skip; ++i) it.next();
         return new Iterator<A>() {
            int counter = 0;
            @Override
            public boolean hasNext() {
               return counter < limit && it.hasNext();
            }

            @Override
            public A next() {
               if (counter++ < limit) return it.next();
               else throw new IllegalStateException();
            }

            @Override
            public void remove() {
               throw new UnsupportedOperationException();
            }
         };
      }

      @Override
      public Object[] toArray() {
         Object[] array = new Object[size()];
         int skipped = 0;
         int count = 0;
         for (A a : collection) {
            if (skipped++ < skip) continue;
            if (count >= limit) break;
            array[count++] = a;
         }
         return array;
      }

      @Override
      public <T> T[] toArray(T[] array) {
         if (array.length < size()) {
            array = (T[]) Array.newInstance(array.getClass().getComponentType(), size());
         }
         int skipped = 0;
         int count = 0;
         for (A a : collection) {
            if (skipped++ < skip) continue;
            if (count >= limit) break;
            array[count++] = (T) a;
         }
         return array;
      }

      @Override
      public boolean add(A a) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean remove(Object o) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean containsAll(Collection<?> c) {
         Set set = new HashSet(c);
         int skipped = 0, count = 0;
         for (A a : collection) {
            if (skipped++ < skip) continue;
            if (count++ >= limit) break;
            if (set.contains(a)) set.remove(a);
            if (set.isEmpty()) return true;
         }
         return set.isEmpty();
      }

      @Override
      public boolean addAll(Collection<? extends A> c) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean removeAll(Collection<?> c) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean retainAll(Collection<?> c) {
         throw new UnsupportedOperationException();
      }

      @Override
      public void clear() {
         throw new UnsupportedOperationException();
      }
   }
}

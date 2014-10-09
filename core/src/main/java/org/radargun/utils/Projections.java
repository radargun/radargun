package org.radargun.utils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/**
 * Helper class for creating projections.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Projections {
   public static <A, B> Collection<B> project(Collection<A> collection, Func<A, B> func) {
      return new ProjectCollection<A, B>(collection, func);
   }

   public static <A, B> List<B> project(List<A> collection, Func<A, B> func) {
      return new ProjectList<A, B>(collection, func);
   }

   public static <A, B extends A> List<B> castProject(List<A> list, Class<B> clazz) {
      return new ProjectList<A, B>(list, new Func<A, B>() {
         @Override
         public B project(A a) {
            return (B) a;
         }
      });
   }

   public static <A> boolean any(Collection<A> collection, Condition<A> condition) {
      for (A a : collection) {
         if (condition.accept(a)) return true;
      }
      return false;
   }

   public static <A> boolean all(Collection<A> collection, Condition<A> condition) {
      for (A a : collection) {
         if (!condition.accept(a)) return false;
      }
      return true;
   }

   public static <A> Collection<A> subset(Collection<A> collection, int skip, int limit) {
      return new SubsetCollection<A>(collection, skip, limit);
   }

   public static <A, B> Collection<B> where(Collection<A> collection, Condition<A> condition, Func<A, B> converter) {
      // TODO: lazy collection would be better
      ArrayList<B> out = new ArrayList<B>();
      for (A a : collection) {
         if (condition.accept(a)) {
            out.add(converter.project(a));
         }
      }
      return out;
   }

   public static <A extends Comparable> A max(Collection<A> collection) {
      A max = null;
      for (A a : collection) {
         if (a == null) continue;
         else if (max == null) max = a;
         else if (max.compareTo(a) < 0) max = a;
      }
      return max;
   }

   public static <A, B> Collection<B> instancesOf(Collection<A> collection, final Class<? extends B> clazz) {
      return where(collection, new Condition<A>() {
         @Override
         public boolean accept(A a) {
            return clazz.isInstance(a);
         }
      }, new Func<A, B>() {
         @Override
         public B project(A a) {
            return clazz.cast(a);
         }
      });
   }

   public static <A> Collection<A> notNull(Collection<A> collection) {
      return where(collection, new Condition<A>() {
         @Override
         public boolean accept(A a) {
            return a != null;
         }
      }, Identity.INSTANCE);
   }

   public static long[] toLongArray(Collection<Long> collection) {
      long[] array = new long[collection.size()];
      int i = 0;
      for (Long l : collection) {
         array[i++] = l;
      }
      return array;
   }

   public interface Func<A, B> {
      B project(A a);
   }

   public interface Condition<A> {
      boolean accept(A a);
   }

   public static class Identity<A> implements Func<A, A> {
      public static final Identity INSTANCE = new Identity();
      @Override
      public A project(A a) {
         return a;
      }
   }

   private static class ProjectCollection<A, B> implements Collection<B> {
      private final Collection<A> collection;
      protected final Func<A, B> func;

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

   private static class ProjectList<A, B> extends ProjectCollection<A,B> implements List<B> {
      private final List<A> list;

      public ProjectList(List<A> list, Func<A, B> func) {
         super(list, func);
         this.list = list;
      }

      @Override
      public boolean addAll(int index, Collection<? extends B> c) {
         throw new UnsupportedOperationException();
      }

      @Override
      public B get(int index) {
         return func.project(list.get(index));
      }

      @Override
      public B set(int index, B element) {
         throw new UnsupportedOperationException();
      }

      @Override
      public void add(int index, B element) {
         throw new UnsupportedOperationException();
      }

      @Override
      public B remove(int index) {
         throw new UnsupportedOperationException();
      }

      @Override
      public int indexOf(Object o) {
         for (int i = 0; i < list.size(); ++i) {
            B b = func.project(list.get(i));
            if (b == null) {
               if (o == null) return i;
            } else {
               if (b.equals(o)) return i;
            }
         }
         return -1;
      }

      @Override
      public int lastIndexOf(Object o) {
         for (int i = list.size(); i >= 0; --i) {
            B b = func.project(list.get(i));
            if (b == null) {
               if (o == null) return i;
            } else {
               if (b.equals(o)) return i;
            }
         }
         return -1;
      }

      @Override
      public ListIterator<B> listIterator() {
         return new ProjectedListIterator(list.listIterator());
      }

      @Override
      public ListIterator<B> listIterator(int index) {
         return new ProjectedListIterator(list.listIterator(index));
      }

      @Override
      public List<B> subList(int fromIndex, int toIndex) {
         throw new UnsupportedOperationException();
      }

      private class ProjectedListIterator implements ListIterator<B> {
         private final ListIterator<A> it;

         public ProjectedListIterator(ListIterator<A> it) {
            this.it = it;
         }

         @Override
         public boolean hasNext() {
            return it.hasNext();
         }

         @Override
         public B next() {
            return func.project(it.next());
         }

         @Override
         public boolean hasPrevious() {
            return it.hasPrevious();
         }

         @Override
         public B previous() {
            return func.project(it.previous());
         }

         @Override
         public int nextIndex() {
            return it.nextIndex();
         }

         @Override
         public int previousIndex() {
            return it.previousIndex();
         }

         @Override
         public void remove() {
            throw new UnsupportedOperationException();
         }

         @Override
         public void set(B b) {
            throw new UnsupportedOperationException();
         }

         @Override
         public void add(B b) {
            throw new UnsupportedOperationException();
         }
      }
   }
}

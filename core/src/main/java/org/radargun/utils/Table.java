package org.radargun.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Random-accessible table iterable over both rows and columns
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Table<TRowKey, TColumnKey, TValue> {
   private List<List<TValue>> data = new ArrayList<List<TValue>>();
   private LinkedHashMap<TRowKey, Integer> rowIndices = new LinkedHashMap<TRowKey, Integer>();
   private LinkedHashMap<TColumnKey, Integer> columnIndices = new LinkedHashMap<TColumnKey, Integer>();
   private TValue defaultValue = null;

   public Table() {
   }

   public Table(TValue defaultValue) {
      this.defaultValue = defaultValue;
   }

   public Set<TRowKey> rowKeys() {
      return Collections.unmodifiableSet(rowIndices.keySet());
   }

   public Set<TColumnKey> columnKeys() {
      return Collections.unmodifiableSet(columnIndices.keySet());
   }

   public TValue put(TRowKey row, TColumnKey column, TValue value) {
      List<TValue> rowList = ensureRow(row);
      int columnIndex = ensureColumn(column, rowList);
      TValue previous = rowList.get(columnIndex);
      rowList.set(columnIndex, value);
      return previous;
   }

   protected TValue set(int rowIndex, int columnIndex, TValue value) {
      if (data.size() <= rowIndex) throw new IllegalArgumentException();
      List<TValue> rowList = data.get(rowIndex);
      for (int i = rowList.size(); i <= columnIndex; ++i) {
         rowList.add(defaultValue);
      }
      return rowList.set(columnIndex, value);
   }

   private int ensureColumn(TColumnKey column, List<TValue> row) {
      Integer index = columnIndices.get(column);
      if (index == null) {
         index = columnIndices.size();
         columnIndices.put(column, index);
      }
      for (int i = row.size(); i <= index; ++i) {
         row.add(defaultValue);
      }
      return index;
   }

   private List<TValue> ensureRow(TRowKey row) {
      Integer index = rowIndices.get(row);
      if (index == null) {
         index = rowIndices.size();
         if (index != data.size()) {
            throw new IllegalStateException();
         }
         data.add(new ArrayList<TValue>());
         rowIndices.put(row, index);
      }
      return data.get(index);
   }

   public TValue get(TRowKey row, TColumnKey column) {
      Integer rowIndex = rowIndices.get(row);
      if (rowIndex == null) return defaultValue;
      Integer columnIndex = columnIndices.get(column);
      if (columnIndex == null) return defaultValue;
      List<TValue> rowList = data.get(rowIndex);
      if (rowList.size() <= columnIndex) return defaultValue;
      return rowList.get(columnIndex);
   }

   protected TValue get(int rowIndex, int columnIndex) {
      if (data.size() <= rowIndex) return defaultValue;
      List<TValue> rowList = data.get(rowIndex);
      if (rowList.size() > columnIndex) {
         return rowList.get(columnIndex);
      } else {
         return defaultValue;
      }
   }

   public Map<TRowKey, TValue> getColumn(TColumnKey column) {
      return new ColumnMap(column);
   }

   public Map<TColumnKey, TValue> getRow(TRowKey row) {
      return new RowMap(row);
   }

   public boolean columnContains(int columnIndex, Object value) {
      for (List<TValue> rowList : data) {
         if (rowList.size() >= columnIndex) {
            return areEqual(rowList.get(columnIndex), value);
         }
      }
      return false;
   }

   public boolean rowContains(int rowIndex, Object value) {
      if (data.size() > rowIndex) throw new IllegalArgumentException();
      List<TValue> rowList = data.get(rowIndex);
      return rowList.contains(value) || (rowList.size() < columnIndices.size() && areEqual(value, defaultValue));
   }

   protected boolean areEqual(Object v1, Object v2) {
      if (v1 == null) return v2 == null;
      else return v1.equals(v2);
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      for (TColumnKey columnKey : columnIndices.keySet()) {
         sb.append(";").append(columnKey.toString());
      }
      Iterator<TRowKey> rowKeyIterator = rowIndices.keySet().iterator();
      for (List<TValue> row : data) {
         sb.append('\n').append(rowKeyIterator.next());
         for (TValue value : row) {
            sb.append(";").append(value);
         }
      }
      return sb.toString();
   }

   protected abstract class AbstractColumn {
      protected TColumnKey column;
      protected int columnIndex = -1;

      private AbstractColumn(TColumnKey column) {
         this.column = column;
      }

      public int size() {
         return rowIndices.size();
      }

      public boolean isEmpty() {
         return rowIndices.size() == 0;
      }

      public void clear() {
         if (ensure()) {
            for (List<TValue> rowList : data) {
               if (rowList.size() > columnIndex) {
                  rowList.set(columnIndex, defaultValue);
               }
            }
         }
      }

      protected boolean ensure() {
         if (columnIndex < 0) {
            Integer index = columnIndices.get(column);
            if (index != null) {
               columnIndex = index;
            } else {
               return false;
            }
         }
         return true;
      }
   }

   protected class ColumnMap extends AbstractColumn implements Map<TRowKey, TValue> {

      private ColumnMap(TColumnKey column) {
         super(column);
      }

      @Override
      public boolean containsKey(Object key) {
         return rowIndices.containsKey(key);
      }

      @Override
      public boolean containsValue(Object value) {
         if (ensure()) {
            if (columnContains(columnIndex, value)) return true;
         }
         return false;
      }

      @Override
      public TValue get(Object key) {
         if (ensure()) {
            Integer rowIndex = rowIndices.get(key);
            if (rowIndex == null) return defaultValue;
            return data.get(rowIndex).get(columnIndex);
         }
         return defaultValue;
      }

      @Override
      public TValue put(TRowKey key, TValue value) {
         return Table.this.put(key, column, value);
      }

      @Override
      public TValue remove(Object key) {
         return Table.this.put((TRowKey) key, column, defaultValue);
      }

      @Override
      public void putAll(Map<? extends TRowKey, ? extends TValue> m) {
         for (Map.Entry<? extends TRowKey, ? extends TValue> entry : m.entrySet()) {
            Table.this.put(entry.getKey(), column, entry.getValue());
         }
      }

      @Override
      public Set<TRowKey> keySet() {
         return Collections.unmodifiableSet(rowIndices.keySet());
      }

      @Override
      public Collection<TValue> values() {
         return new ColumnCollection(column);
      }

      @Override
      public Set<Map.Entry<TRowKey, TValue>> entrySet() {
         return new ColumnSet(column);
      }
   }

   protected class ColumnCollection extends AbstractColumn implements Collection<TValue> {
      private ColumnCollection(TColumnKey column) {
         super(column);
      }

      @Override
      public boolean contains(Object value) {
         if (ensure()) {
            return columnContains(columnIndex, value);
         } else {
            return false;
         }
      }

      @Override
      public Iterator<TValue> iterator() {
         return new Iterator<TValue>() {
            private int rowIndex = 0;

            @Override
            public boolean hasNext() {
               if (ensure()) {
                  return rowIndex < data.size();
               } else {
                  return false;
               }
            }

            @Override
            public TValue next() {
               if (ensure()) {
                  return Table.this.get(rowIndex++, columnIndex);
               } else {
                  throw new IllegalStateException();
               }
            }

            @Override
            public void remove() {
               if (rowIndex == 0) throw new IllegalStateException();
               if (ensure()) {
                  Table.this.set(rowIndex - 1, columnIndex, defaultValue);
               } else {
                  throw new IllegalStateException();
               }
            }
         };
      }

      @Override
      public Object[] toArray() {
         if (ensure()) {
            Object[] array = new Object[data.size()];
            return toArray(array);
         }
         return new Object[0];
      }

      @Override
      public <T> T[] toArray(T[] a) {
         if (ensure()) {
            if (a.length < data.size()) {
               a = (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), data.size());
            }
            for (int i = 0; i < data.size(); ++i) {
               a[i] = (T) Table.this.get(i, columnIndex);
            }
            return a;
         } else {
            return (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), 0);
         }
      }

      @Override
      public boolean add(TValue tValue) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean remove(Object o) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean containsAll(Collection<?> c) {
         if (ensure()) {
            for (Object value : c) {
               if (!columnContains(columnIndex, value)) return false;
            }
            return true;
         } else {
            return c.isEmpty();
         }
      }

      @Override
      public boolean addAll(Collection<? extends TValue> c) {
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
   }

   protected class ColumnSet extends AbstractColumn implements Set<Map.Entry<TRowKey, TValue>> {
      public ColumnSet(TColumnKey column) {
         super(column);
      }

      @Override
      public boolean contains(Object o) {
         Map.Entry<TRowKey, TValue> entry = (Map.Entry<TRowKey, TValue>) o;
         if (ensure()) {
            Integer rowIndex = rowIndices.get(entry.getKey());
            if (rowIndex == null) return false;
            List<TValue> rowList = data.get(rowIndex);
            if (rowList.size() <= columnIndex) return areEqual(entry.getValue(), defaultValue);
            TValue item = rowList.get(columnIndex);
            return areEqual(item, entry.getValue());
         } else return false;
      }

      @Override
      public Iterator<Map.Entry<TRowKey, TValue>> iterator() {
         return new Iterator<Map.Entry<TRowKey, TValue>>() {
            private int rowIndex = 0;
            private Iterator<TRowKey> rowKeyIterator;

            private boolean ensure() {
               if (ColumnSet.this.ensure()) {
                  if (rowKeyIterator == null) {
                     rowKeyIterator = rowIndices.keySet().iterator();
                  }
                  return true;
               }
               return false;
            }

            @Override
            public boolean hasNext() {
               return ensure() && rowIndex < data.size();
            }

            @Override
            public Map.Entry<TRowKey, TValue> next() {
               if (ensure()) {
                  return new Entry<TRowKey, TValue>(rowKeyIterator.next(), get(rowIndex++, columnIndex));
               } else {
                  throw new IllegalStateException();
               }
            }

            @Override
            public void remove() {
               if (rowIndex == 0) throw new IllegalStateException();
               if (ensure()) {
                  set(rowIndex - 1, columnIndex, defaultValue);
               } else {
                  throw new IllegalStateException();
               }
            }
         };
      }

      @Override
      public Object[] toArray() {
         if (ensure()) {
            return toArray(new Map.Entry[data.size()]);
         } else {
            return new Object[0];
         }
      }

      @Override
      public <T> T[] toArray(T[] a) {
         if (ensure()) {
            if (a.length < data.size()) {
               a = (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), data.size());
            }
            Iterator<TRowKey> rowKeyIterator = rowIndices.keySet().iterator();
            for (int i = 0; i < data.size(); ++i) {
               a[i] = (T) new Entry(rowKeyIterator.next(), Table.this.get(i, columnIndex));
            }
            return a;
         } else {
            return (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), 0);
         }
      }

      @Override
      public boolean add(Map.Entry<TRowKey, TValue> entry) {
         return !areEqual(Table.this.put(entry.getKey(), column, entry.getValue()), entry.getValue());
      }

      @Override
      public boolean remove(Object o) {
         if (ensure()) {
            Map.Entry<TRowKey, TValue> entry = (Map.Entry<TRowKey, TValue>) o;
            Integer rowIndex = rowIndices.get(entry.getKey());
            if (rowIndex == null) return false;
            if (areEqual(Table.this.get(rowIndex, columnIndex), entry.getValue())) {
               Table.this.set(rowIndex, columnIndex, defaultValue);
               return true;
            }
         }
         return false;
      }

      @Override
      public boolean containsAll(Collection<?> c) {
         for (Object o : c) {
            if (!contains(o)) return false;
         }
         return true;
      }

      @Override
      public boolean addAll(Collection<? extends Map.Entry<TRowKey, TValue>> c) {
         boolean changed = false;
         for (Map.Entry<TRowKey, TValue> entry : c) {
            changed = add(entry) || changed;
         }
         return changed;
      }

      @Override
      public boolean retainAll(Collection<?> c) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean removeAll(Collection<?> c) {
         boolean changed = false;
         for (Object o : c) {
            changed = remove(o) || changed;
         }
         return changed;
      }
   }

   protected static class Entry<TKey, TValue> implements Map.Entry<TKey, TValue> {
      private TKey key;
      private TValue value;

      public Entry(TKey key, TValue value) {
         this.key = key;
         this.value = value;
      }

      @Override
      public TKey getKey() {
         return key;
      }

      @Override
      public TValue getValue() {
         return value;
      }

      @Override
      public TValue setValue(TValue value) {
         throw new UnsupportedOperationException();
      }
   }

   protected abstract class AbstractRow {
      protected TRowKey row;
      protected int rowIndex = -1;
      protected List<TValue> rowList;

      protected AbstractRow(TRowKey row) {
         this.row = row;
      }

      public int size() {
         return columnIndices.size();
      }

      public boolean isEmpty() {
         return columnIndices.size() == 0;
      }

      public void clear() {
         if (ensure()) {
            rowList.clear();
         }
      }

      protected boolean ensure() {
         if (rowIndex < 0) {
            Integer index = rowIndices.get(row);
            if (index != null) {
               rowIndex = index;
               rowList = data.get(rowIndex);
            } else {
               return false;
            }
         }
         return true;
      }
   }

   protected class RowMap extends AbstractRow implements Map<TColumnKey, TValue> {
      public RowMap(TRowKey row) {
         super(row);
      }

      @Override
      public boolean containsKey(Object key) {
         return columnIndices.containsKey(key);
      }

      @Override
      public boolean containsValue(Object value) {
         if (ensure()) {
            return rowContains(rowIndex, value);
         } else {
            return false;
         }
      }

      @Override
      public TValue get(Object key) {
         if (ensure()) {
            Integer columnIndex = columnIndices.get(key);
            if (columnIndex != null) {
               return Table.this.get(rowIndex, columnIndex);
            }
         }
         return defaultValue;
      }

      @Override
      public TValue put(TColumnKey key, TValue value) {
         return Table.this.put(row, key, value);
      }

      @Override
      public TValue remove(Object key) {
         return Table.this.put(row, (TColumnKey) key, defaultValue);
      }

      @Override
      public void putAll(Map<? extends TColumnKey, ? extends TValue> m) {
         for (Entry<? extends TColumnKey, ? extends TValue> entry : m.entrySet()) {
            Table.this.put(row, entry.getKey(), entry.getValue());
         }
      }

      @Override
      public Set<TColumnKey> keySet() {
         return Collections.unmodifiableSet(columnIndices.keySet());
      }

      @Override
      public Collection<TValue> values() {
         return new RowCollection(row);
      }

      @Override
      public Set<Entry<TColumnKey, TValue>> entrySet() {
         return new RowSet(row);
      }
   }

   public class RowCollection extends AbstractRow implements Collection<TValue> {

      protected RowCollection(TRowKey row) {
         super(row);
      }

      @Override
      public boolean contains(Object o) {
         if (ensure()) {
            return rowContains(rowIndex, o);
         } else {
            return false;
         }
      }

      @Override
      public Iterator<TValue> iterator() {
         return new Iterator<TValue>() {
            int index = 0;

            @Override
            public boolean hasNext() {
               if (ensure()) {
                  return index < rowList.size();
               } else {
                  return false;
               }
            }

            @Override
            public TValue next() {
               if (ensure()) {
                  return rowList.get(index++);
               } else {
                  throw new IllegalStateException();
               }
            }

            @Override
            public void remove() {
               if (index <= 0) throw new IllegalStateException();
               if (ensure()) {
                  rowList.set(index - 1, defaultValue);
               } else {
                  throw new IllegalStateException();
               }
            }
         };
      }

      @Override
      public Object[] toArray() {
         if (ensure()) {
            return toArray(new Object[columnIndices.size()]);
         } else {
            return new Object[0];
         }
      }

      @Override
      public <T> T[] toArray(T[] a) {
         if (ensure()) {
            if (a.length < columnIndices.size()) {
               a = (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), columnIndices.size());
            }
            for (int i = 0; i < rowList.size(); ++i) {
               a[i] = (T) rowList.get(i);
            }
            for (int i = rowList.size(); i < columnIndices.size(); ++i) {
               a[i] = (T) defaultValue;
            }
            return a;
         } else {
            return (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), 0);
         }
      }

      @Override
      public boolean add(TValue tValue) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean remove(Object o) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean containsAll(Collection<?> c) {
         if (ensure()) {
            for (Object o : c) {
               if (rowContains(rowIndex, o)) return false;
            }
            return true;
         } else {
            return c.isEmpty();
         }
      }

      @Override
      public boolean addAll(Collection<? extends TValue> c) {
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
   }

   public class RowSet extends AbstractRow implements Set<Map.Entry<TColumnKey, TValue>> {

      protected RowSet(TRowKey row) {
         super(row);
      }

      @Override
      public boolean contains(Object o) {
         if (ensure()) {
            Map.Entry<TColumnKey, TValue> entry = (Map.Entry<TColumnKey, TValue>) o;
            Integer columnIndex = columnIndices.get(entry.getKey());
            if (columnIndex != null) {
               return areEqual(rowList.get(columnIndex), entry.getValue());
            }
         }
         return false;
      }

      @Override
      public Iterator<Map.Entry<TColumnKey, TValue>> iterator() {
         return new Iterator<Map.Entry<TColumnKey, TValue>>() {
            int index = 0;
            Iterator<TColumnKey> columnKeyIterator;

            private boolean ensure() {
               if (RowSet.this.ensure()) {
                  if (columnKeyIterator == null) {
                     columnKeyIterator = columnIndices.keySet().iterator();
                  }
                  return true;
               }
               return false;
            }

            @Override
            public boolean hasNext() {
               if (ensure()) {
                  return index < rowList.size();
               } else {
                  return false;
               }
            }

            @Override
            public Map.Entry<TColumnKey, TValue> next() {
               if (ensure()) {
                  return new Entry(columnKeyIterator.next(), rowList.get(index++));
               } else {
                  throw new IllegalStateException();
               }
            }

            @Override
            public void remove() {
               if (index <= 0) throw new IllegalStateException();
               if (ensure()) {
                  rowList.set(index - 1, defaultValue);
               } else {
                  throw new IllegalStateException();
               }
            }
         };
      }

      @Override
      public Object[] toArray() {
         if (ensure()) {
            return toArray(new Object[columnIndices.size()]);
         } else {
            return new Object[0];
         }
      }

      @Override
      public <T> T[] toArray(T[] a) {
         if (ensure()) {
            if (a.length < columnIndices.size()) {
               a = (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), columnIndices.size());
            }
            Iterator<TColumnKey> columnKeyIterator = columnIndices.keySet().iterator();
            for (int i = 0; i < rowList.size(); ++i) {
               a[i] = (T) new Entry(columnKeyIterator.next(), rowList.get(i));
            }
            for (int i = rowList.size(); i < columnIndices.size(); ++i) {
               a[i] = (T) new Entry(columnKeyIterator.next(), defaultValue);
            }
            return a;
         } else {
            return (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), 0);
         }
      }

      @Override
      public boolean add(Map.Entry<TColumnKey, TValue> entry) {
         return !areEqual(Table.this.put(row, entry.getKey(), entry.getValue()), entry.getValue());
      }

      @Override
      public boolean remove(Object o) {
         if (ensure()) {
            Map.Entry<TColumnKey, TValue> entry = (Map.Entry<TColumnKey, TValue>) o;
            Integer columnIndex = columnIndices.get(entry.getKey());
            if (columnIndex != null) {
               if (rowList.size() <= columnIndex) {
                  return false;
               }
               if (!areEqual(entry.getValue(), rowList.get(columnIndex))) {
                  rowList.set(columnIndex, entry.getValue());
                  return true;
               }
            }
         }
         return false;
      }

      @Override
      public boolean containsAll(Collection<?> c) {
         for (Object o : c) {
            if (!contains(o)) return false;
         }
         return true;
      }

      @Override
      public boolean addAll(Collection<? extends Map.Entry<TColumnKey, TValue>> c) {
         boolean changed = false;
         for (Map.Entry<TColumnKey, TValue> entry : c) {
            changed = add(entry) || changed;
         }
         return changed;
      }

      @Override
      public boolean retainAll(Collection<?> c) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean removeAll(Collection<?> c) {
         boolean changed = false;
         for (Object o : c) {
            changed = remove(o) || changed;
         }
         return changed;
      }
   }
}

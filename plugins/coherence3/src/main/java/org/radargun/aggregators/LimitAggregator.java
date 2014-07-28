package org.radargun.aggregators;

import java.io.IOException;
import java.util.*;

import com.tangosol.io.pof.PofReader;
import com.tangosol.io.pof.PofWriter;
import com.tangosol.io.pof.PortableObject;
import com.tangosol.util.InvocableMap;
import com.tangosol.util.SimpleMapEntry;
import com.tangosol.util.comparator.EntryComparator;

/**
 * Wraps another aggregator to limit the amount of data returned.
 */
public class LimitAggregator implements InvocableMap.ParallelAwareAggregator, PortableObject {
   private InvocableMap.EntryAggregator aggregator;
   private int limit;
   private boolean ordered;
   private Comparator comparator;
   private int comparatorStyle = EntryComparator.CMP_KEY;

   public LimitAggregator() {
   }

   /**
    * @param comparatorStyle Style from {@link EntryComparator}
    */
   public LimitAggregator(InvocableMap.EntryAggregator aggregator, int limit, boolean ordered, Comparator comparator, int comparatorStyle) {
      this.aggregator = aggregator;
      this.limit = limit;
      this.ordered = ordered;
      this.comparator = comparator;
      this.comparatorStyle = comparatorStyle;
   }

   @Override
   public InvocableMap.EntryAggregator getParallelAggregator() {
      return this;
   }

   @Override
   public Object aggregate(Set entries) {
      return aggregator.aggregate(entries);
   }

   @Override
   public Object aggregateResults(Collection results) {
      Object limited;
      if (aggregator instanceof InvocableMap.ParallelAwareAggregator) {
         Object result = ((InvocableMap.ParallelAwareAggregator) aggregator).aggregateResults(results);
         if (result instanceof Collection) {
            limited = limitCollection((Collection) result);
         } else if (result instanceof Map) {
            limited = limitMap((Map) result);
         } else {
            limited = Collections.singleton(result);
         }
      } else {
         limited = limitCollection(results);
      }
      return limited;
   }

   public <K, V> Map<K, V> limitMap(Map<K, V> map) {
      Map<K, V> limited = cloneMap(map);
      limitInPlace(limited.keySet());
      return limited;
   }

   public <T> Collection<T> limitCollection(Collection<T> collectionToTruncate) {
      Collection<T> collection = cloneCollection(collectionToTruncate);
      limitInPlace(collection);
      return collection;
   }

   private void limitInPlace(Collection collection) {
      Iterator it = collection.iterator();
      int count = 0;
      while (it.hasNext()) {
         it.next();
         if (count < limit) {
            count++;
            continue;
         }
         it.remove();
      }
   }

   public <T> Collection<T> cloneCollection(Collection<T> collection) {
      List list = new ArrayList(collection);
      if (ordered) {
         Collections.sort(list, comparator);
      }
      return list;
   }

   public <K, V> Map<K, V> cloneMap(Map<K, V> map) {
      Map<K, V> newMap;
      if (ordered) {
         // TODO: custom map without the double copy would be better
         newMap = new LinkedHashMap<K, V>();
         List<Map.Entry<K, V>> entries = new ArrayList<Map.Entry<K, V>>();
         for (Map.Entry entry : map.entrySet()) {
            entries.add(new SimpleMapEntry(entry.getKey(), entry.getValue()));
         }
         EntryComparator entryComparator = new EntryComparator(comparator, comparatorStyle);
         Collections.sort(entries, entryComparator);
         for (Map.Entry<K, V> entry : entries) {
            newMap.put(entry.getKey(), entry.getValue());
         }
      } else {
         newMap = new HashMap<K, V>();
         newMap.putAll(map);
      }
      return newMap;
   }

   @Override
   public void readExternal(PofReader pofReader) throws IOException {
      aggregator = (InvocableMap.EntryAggregator) pofReader.readObject(0);
      limit = pofReader.readInt(1);
      comparator = (Comparator) pofReader.readObject(2);
      ordered = pofReader.readBoolean(3);
      comparatorStyle = pofReader.readInt(4);
   }

   @Override
   public void writeExternal(PofWriter pofWriter) throws IOException {
      pofWriter.writeObject(0, aggregator);
      pofWriter.writeInt(1, limit);
      pofWriter.writeObject(2, comparator);
      pofWriter.writeBoolean(3, ordered);
      pofWriter.writeInt(4, comparatorStyle);
   }
}

package org.radargun.processors;

import com.tangosol.util.Filter;
import com.tangosol.util.InvocableMap;
import com.tangosol.util.InvocableMapHelper;
import com.tangosol.util.processor.ConditionalPut;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class ValueConditionalPut extends ConditionalPut {
   public ValueConditionalPut(Filter filter, Object oValue) {
      super(filter, oValue);
   }

   @Override
   public Object process(InvocableMap.Entry entry) {
      if (InvocableMapHelper.evaluateEntry(this.m_filter, entry))
      {
         Object old = entry.getValue();
         entry.setValue(this.m_oValue, false);
         return old;
      }
      return entry.getValue();
   }
}

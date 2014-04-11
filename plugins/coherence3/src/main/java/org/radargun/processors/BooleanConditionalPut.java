package org.radargun.processors;

import com.tangosol.util.Filter;
import com.tangosol.util.InvocableMap;
import com.tangosol.util.InvocableMapHelper;
import com.tangosol.util.processor.ConditionalPut;

/**
 * Conditional put returning boolean return value according to the JSR-107 spec.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class BooleanConditionalPut extends ConditionalPut {
   public BooleanConditionalPut(Filter filter, Object oValue) {
      super(filter, oValue);
   }

   @Override
   public Object process(InvocableMap.Entry entry) {
      if (InvocableMapHelper.evaluateEntry(this.m_filter, entry))
      {
         entry.setValue(this.m_oValue, false);
         return Boolean.TRUE;
      }
      return Boolean.FALSE;
   }
}

package org.radargun.processors;

import com.tangosol.util.Filter;
import com.tangosol.util.InvocableMap;
import com.tangosol.util.InvocableMapHelper;
import com.tangosol.util.processor.ConditionalRemove;

/**
 * Conditional remove returning boolean return value according to the JSR-107 spec.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class BooleanConditionalRemove extends ConditionalRemove {
   public BooleanConditionalRemove() {
   }

   public BooleanConditionalRemove(Filter filter) {
      super(filter);
   }

   @Override
   public Object process(InvocableMap.Entry entry) {
      if ((entry.isPresent()) && (InvocableMapHelper.evaluateEntry(this.m_filter, entry))) {
         entry.remove(false);
         return Boolean.TRUE;
      }
      return Boolean.FALSE;
   }
}

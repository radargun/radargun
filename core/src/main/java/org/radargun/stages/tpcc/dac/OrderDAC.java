package org.radargun.stages.tpcc.dac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.radargun.stages.tpcc.TpccTools;
import org.radargun.stages.tpcc.domain.Order;
import org.radargun.traits.BasicOperations;

/**
 * @author Sebastiano Peluso &lt;peluso@gsd.inesc-id.pt, peluso@dis.uniroma1.it&gt;
 */
public final class OrderDAC {

   private OrderDAC() {
   }

   public static Order loadByGreatestId(BasicOperations.Cache basicCache, long w_id, long d_id, long c_id) throws Throwable {
      List<Order> list = new ArrayList<Order>();
      boolean found = false;
      Order current = null;

      for (int id_order = 1; id_order <= TpccTools.NB_MAX_ORDER; id_order++) {
         current = new Order();
         current.setO_id(id_order);
         current.setO_w_id(w_id);
         current.setO_d_id(d_id);
         found = current.load(basicCache);
         if (found && current.getO_c_id() == c_id) {
            list.add(current);
         }
      }

      if (list.isEmpty()) return null;
      Collections.sort(list);  // Decreasing order of o_id
      return list.iterator().next();
   }
}

package org.radargun.stages.tpcc.dac;

import java.util.ArrayList;
import java.util.List;

import org.radargun.stages.tpcc.domain.Order;
import org.radargun.stages.tpcc.domain.OrderLine;
import org.radargun.traits.BasicOperations;

/**
 * @author Sebastiano Peluso &lt;peluso@gsd.inesc-id.pt, peluso@dis.uniroma1.it&gt;
 */
public final class OrderLineDAC {

   private OrderLineDAC() {
   }

   public static List<OrderLine> loadByOrder(BasicOperations.Cache basicCache, Order order) throws Throwable {
      List<OrderLine> list = new ArrayList<OrderLine>();
      if (order == null) return list;
      int numLines = order.getO_ol_cnt();
      OrderLine current = null;
      boolean found = false;

      for (int i = 0; i < numLines; i++) {
         current = new OrderLine();
         current.setOl_w_id(order.getO_w_id());
         current.setOl_d_id(order.getO_d_id());
         current.setOl_o_id(order.getO_id());
         current.setOl_number(i);
         found = current.load(basicCache);
         if (found) list.add(current);
      }
      return list;
   }
}

package org.radargun.http.service;

import java.math.BigDecimal;

import org.radargun.Service;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.Fuzzy;

/**
 * RestEasy REST client for general Web applications
 *
 * @author Martin Gencur
 */
@Service(doc = "RestEasy REST client for general Web applications")
public class RESTEasyService extends AbstractRESTEasyService  {

   @Property(doc = "Ratio between the number of connections to individual servers. " +
                   "Servers from the 'servers' list are indexed from 0. When the client " +
                   "is first created it will choose a server to communicate with according " +
                   "to this load balancing setting. The client will keep communicating with " +
                   "this single server until redirected.", converter = Fuzzy.IntegerConverter.class)
   //By default clients are evenly spread across all servers. See {@link #init()}.
   protected Fuzzy<Integer> serversLoadBalance;

   @ProvidesTrait
   public RESTEasyOperations createOperations() {
      return new RESTEasyOperations(this);
   }

   @Init
   public void init() {
      if (serversLoadBalance == null) {
         Fuzzy.Builder<Integer> builder = new Fuzzy.Builder<>();
         for (int i=0; i!=getServers().size(); i++) {
            builder.addWeighted(i, BigDecimal.ONE);
         }
         serversLoadBalance = builder.create();
      } else {
         for (Integer serverIndex: serversLoadBalance.getProbabilityMap().keySet()) {
            if (serverIndex >= getServers().size())
               throw new IllegalStateException("Load balancing settings for the REST client include server index " +
                  "which is not in the server list: " + serverIndex);
         }
      }
   }

   public Fuzzy<Integer> getServersLoadBalance() {
      return serversLoadBalance;
   }
}

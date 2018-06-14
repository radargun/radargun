package org.radargun.stages;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.List;

import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;
import org.radargun.utils.ReflexiveConverters;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.TimeService;

/**
 * Inspired by org.jgroups.tests.Probe.
 *
 * TODO https://github.com/radargun/radargun/issues/551
 *
 * @author Matej Cimbora
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
public abstract class AbstractJGroupsProbeStage extends AbstractDistStage {

   private static final String[] EMPTY_RESPONSE = new String[0];

   @Property(doc = "List of queries to be performed.", optional = false, complexConverter = JGroupsProbeStage.ListConverter.class)
   private List<Query> queries = new ArrayList<>();

   @Property(doc = "Diagnostic address to send queries to. Default is 224.0.75.75.")
   private String address = "224.0.75.75";

   @Property(doc = "Diagnostic port. Default is 7500.")
   private int port = 7500;

   @Property(doc = "Maximum time to wait for query responses. Default is 60 seconds. Valid only when used in conjunction " +
      "with expectedResponseCount parameter.", converter = TimeConverter.class)
   private long timeout = 60_000;

   @Property(doc = "Minimum number of responses to wait for. Default is -1 don't wait for responses.", optional = false)
   private int expectedResponseCount = -1;

   @Property(doc = "Print results of operation to log (INFO level). By default trace logging needs to be enabled.")
   protected boolean printResultsAsInfo;

   protected String[] run() throws IOException {
      String[] datagramPacketsResponse = null;
      if (!queries.isEmpty() && expectedResponseCount > 0) {
         datagramPacketsResponse = new String[expectedResponseCount];
         MulticastSocket socket = null;
         try {
            socket = new MulticastSocket();
            socket.setSoTimeout((int) timeout);
            InetAddress targetAddress = InetAddress.getByName(address);
            byte[] payload = getQuery().getBytes();
            DatagramPacket packet = new DatagramPacket(payload, 0, payload.length, targetAddress, port);
            socket.send(packet);

            long start = TimeService.currentTimeMillis();
            int received = 0;
            while (received < expectedResponseCount) {
               if (start + timeout < TimeService.currentTimeMillis()) {
                  String errorMessage = "Timed out waiting for query responses";
                  throw new IOException(errorMessage);
               }
               byte[] buffer = new byte[1024 * 64];
               DatagramPacket result = new DatagramPacket(buffer, buffer.length);
               socket.receive(result);
               datagramPacketsResponse[received++] = String.format("Received response %s from %s:%d", new String(result.getData(), 0, result.getLength()), result.getAddress(), result.getPort());
            }
         } catch (IOException e) {
            String errorMessage = "Exception while performing multicast socket operation. Make sure 'enable_diagnostics' property of TP is enabled " +
               "and correct diagnostics port is specified";
            throw new IOException(errorMessage, e);
         } finally {
            if (socket != null) {
               socket.close();
            }
         }
      }
      return datagramPacketsResponse != null ? datagramPacketsResponse : EMPTY_RESPONSE;
   }

   private String getQuery() {
      if (queries.size() == 1) {
         return queries.get(0).query;
      }
      StringBuilder queryBuilder = new StringBuilder();
      for (AbstractJGroupsProbeStage.Query query : queries) {
         queryBuilder.append(query.query).append(" ");
      }
      return queryBuilder.toString();
   }

   @DefinitionElement(name = "query", doc = "Single query element.")
   private static class Query {
      @Property(doc = "Query to be performed.", name = "value", optional = false)
      private String query;
   }

   protected static class ListConverter extends ReflexiveConverters.ListConverter {
      public ListConverter() {
         super(new Class<?>[] {AbstractJGroupsProbeStage.Query.class});
      }
   }
}

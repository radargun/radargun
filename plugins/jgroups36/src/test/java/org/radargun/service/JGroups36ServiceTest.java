package org.radargun.service;

import java.util.Arrays;

import org.jgroups.JChannel;

/**
 * Manual test to verify that replication with primary-replicates-puts="true" works
 * @author Bela Ban
 * @since plugin 3.6.6
 */
public class JGroups36ServiceTest {
   protected static final String config_file = "shared_loopback.xml";
   protected static final int numOwners = 2;
   protected JGroups36Service as, bs, cs, ds, es;

   protected void start() throws Exception {
      as = new JGroups36Service(config_file, "A").numOwners(numOwners);
      as.start();

      bs = new JGroups36Service(config_file, "B").numOwners(numOwners);
      bs.start();

      cs = new JGroups36Service(config_file, "C").numOwners(numOwners);
      cs.start();

      ds = new JGroups36Service(config_file, "D").numOwners(numOwners);
      ds.start();

      es = new JGroups36Service(config_file, "E").numOwners(numOwners);
      es.start();


      //Util.waitUntilAllChannelsHaveSameSize(10000, 500, as.ch, bs.ch, cs.ch, ds.ch, es.ch);

      for (JChannel ch : Arrays.asList(as.ch, bs.ch, cs.ch, ds.ch, es.ch))
         System.out.printf("%s: %s\n", ch.getAddress(), ch.getView());

      for (int i = 1; i < 10; i++) {
         cs.put("key-" + i, "val-" + i);
         System.out.println("");
      }
      for (JGroups36Service service : Arrays.asList(es, ds, cs, bs, as))
         service.stop();
   }


   public static void main(String[] args) throws Exception {
      new JGroups36ServiceTest().start();
   }
}

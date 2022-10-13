package org.radargun.service;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import org.jgroups.protocols.TP;
import org.jgroups.stack.Protocol;
import org.jgroups.stack.ProtocolStack;
import org.jgroups.util.TimeScheduler;
import org.radargun.utils.Utils;

/**
 * JGroupsDumper modified for JGroups 4
 *
 * @author Roman Macor (rmacor@redhat.com)
 */
public class JGroups4Dumper extends JGroupsDumper {

   public JGroups4Dumper(ProtocolStack protocols, long interval) {
      super(protocols, interval);
   }

   @Override
   protected void addDumpers(){
      dumpers.add(new UNICAST3Dumper());
      dumpers.add(new JGroups4Dumper.TPDumper());
   }

   @Override
   protected Map<String, Object> getStats(Protocol protocol){
      return stack.dumpStats(protocol.getName(), null);
   }

   protected static class TPDumper extends JGroupsDumper.TPDumper {
      @Override
      public void dump(Protocol protocol) {
         TP tp = (TP) protocol;
         boolean dump = false;
         dump = checkThreadPool((ThreadPoolExecutor) tp.getThreadPool(), "JGroups") || dump;
         try {
            Field f = TP.class.getDeclaredField("internal_pool");
            f.setAccessible(true);
            dump = checkThreadPool((ThreadPoolExecutor) f.get(tp), "Internal") || dump;
         } catch (Exception e) {
            log.error("Failed to get internal pool");
         }
         TimeScheduler timer = tp.getTimer();
         try {
            Field f = timer.getClass().getDeclaredField("pool");
            f.setAccessible(true);
            dump = checkThreadPool((ThreadPoolExecutor) f.get(timer), "Timer") || dump;
         } catch (Exception e) {
            log.error("Failed to get timer thread pool: " + timer.getClass());
         }
         if (dump) Utils.threadDump();
      }
   }
}

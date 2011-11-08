
import org.radargun.cachewrappers.InfinispanWrapper;
import org.radargun.stressors.PutGetStressor;
import org.radargun.utils.TypedProperties;

import java.util.Properties;

/**
 * @author Mircea Markus
 */
public class Test {
   public static void main(String[] args) throws Exception {
      PutGetStressor pgs = new PutGetStressor();
      pgs.setUseTransactions(true);
      pgs.setNumberOfRequests(100000);
//      pgs.setNumOfThreads(10);
      InfinispanWrapper ir = new InfinispanWrapper();
      String file = "/Users/mmarkus/github/radargun/plugins/infinispan5/src/main/resources/local-config.xml";
      Properties p = new Properties();
      p.put("file", file);
      TypedProperties confAttributes = new TypedProperties(p);
      ir.setUp("ispna-5.0.tx", true, 0, confAttributes);
      pgs.stress(ir);
   }
}

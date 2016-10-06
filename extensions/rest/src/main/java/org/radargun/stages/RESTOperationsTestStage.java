package org.radargun.stages;

import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import org.radargun.Operation;
import org.radargun.RESTOperationInvocations;
import org.radargun.WrappedHttpResponse;
import org.radargun.config.Namespace;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.test.Invocation;
import org.radargun.stages.test.legacy.LegacyStressor;
import org.radargun.stages.test.legacy.LegacyTestStage;
import org.radargun.stages.test.legacy.OperationLogic;
import org.radargun.stages.test.legacy.OperationSelector;
import org.radargun.stages.test.legacy.RatioOperationSelector;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.RESTOperations;

/**
 * A test stage for REST operations on a Web application running in a remote
 * web container. Performing only Get operations.
 *
 * @author Martin Gencur
 */
@Namespace(LegacyTestStage.NAMESPACE)
@Stage(doc = "Test using RESTOperations with specific URL")
public class RESTOperationsTestStage extends LegacyTestStage {

   @Property(doc = "Ratio of GET requests. Default is 1 (100%).")
   protected int getRatio = 1;

   @InjectTrait
   protected RESTOperations restOperations;

   @Override
   protected OperationSelector createOperationSelector() {
      return new RatioOperationSelector.Builder()
         .add(RESTOperations.GET, getRatio)
         .build();
   }

   @Override
   public OperationLogic getLogic() {
      return new StickySessionLogic();
   }

   /**
    * The logic sends GET requests to a predefined URL and keeps track of
    * a session cookie (JSESSIONID). If the logic receives a new session
    * from the remote Web server, it logs an error unless it is a first request.
    * The new session is expected on the first request.
    */
   protected class StickySessionLogic extends OperationLogic {

      private static final String JSESSIONID = "JSESSIONID";

      protected RESTOperations.RESTOperationInvoker restInvoker;
      private boolean isFirstRequest = true;
      private Cookie jsessionid;

      @Override
      public void init(LegacyStressor stressor) {
         super.init(stressor);
         this.restInvoker = restOperations.getRESTInvoker();
         stressor.setUseTransactions(false);//transactions for HTTP ops do not make sense
      }

      @Override
      public void run(Operation operation) throws RequestException {
         WrappedHttpResponse<String> response;
         Invocation invocation;
         if (operation == RESTOperations.GET) {
            List<Cookie> cookies = jsessionid == null ? Collections.EMPTY_LIST : Collections.singletonList(jsessionid);
            invocation = new RESTOperationInvocations.Get(restInvoker, cookies);
         } else {
            throw new IllegalArgumentException(operation.name);
         }
         response = stressor.<WrappedHttpResponse>makeRequest(invocation);
         validateSession(response);
         isFirstRequest = false;
      }

      private void validateSession(WrappedHttpResponse<String> response) {
         NewCookie newSessionId = response.getCookies().get(JSESSIONID);
         if (newSessionId != null) {
            jsessionid = newSessionId.toCookie();
            if (!isFirstRequest) {
               log.error("Session lost!");
            }
            log.info("New session: " + jsessionid);
         }
      }
   }
}

package org.radargun.stages;

import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import org.radargun.Operation;
import org.radargun.RESTOperationInvocations;
import org.radargun.Version;
import org.radargun.config.Namespace;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.test.Invocation;
import org.radargun.stages.test.OperationLogic;
import org.radargun.stages.test.OperationSelector;
import org.radargun.stages.test.RatioOperationSelector;
import org.radargun.stages.test.Stressor;
import org.radargun.stages.test.TestStage;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.RESTOperations;

/**
 * A test stage for REST operations on a Web application running in a remote
 * web container. Performing only Get operations.
 *
 * @author Martin Gencur
 */
@Namespace(name = RESTOperationsTestStage.NAMESPACE)
@Stage(doc = "Test using RESTOperations with specific URL")
public class RESTOperationsTestStage extends TestStage {

   public static final String NAMESPACE = "urn:radargun:stages:rest:" + Version.SCHEMA_VERSION;

   @Property(doc = "Ratio of GET requests. Default is 1 (100%).")
   protected int getRatio = 1;

   /**
    * Context path is the part of URL after the port. It is appended to http://host:port
    * in order to create the full URL.
    */
   @Property(doc = "The context path for this REST stage. Defaults to empty string.")
   private String contextPath = "";

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
      public void init(Stressor stressor) {
         super.init(stressor);
         this.restInvoker = restOperations.getRESTInvoker(contextPath);
         stressor.setUseTransactions(false);//transactions for HTTP ops do not make sense
      }

      @Override
      public void run(Operation operation) throws RequestException {
         Response response;
         Invocation invocation;
         if (operation == RESTOperations.GET) {
            List<Cookie> cookies = jsessionid == null ? Collections.EMPTY_LIST : Collections.singletonList(jsessionid);
            invocation = new RESTOperationInvocations.Get(restInvoker, cookies, null);
         } else {
            throw new IllegalArgumentException(operation.name);
         }
         response = stressor.<Response>makeRequest(invocation);
         validateSession(response);
         isFirstRequest = false;
      }

      private void validateSession(Response response) throws RequestException {
         NewCookie newSessionId = response.getCookies().get(JSESSIONID);
         if (newSessionId != null) {
            jsessionid = newSessionId.toCookie();
            if (!isFirstRequest) {
               throw new IllegalStateException("Session lost!");
            }
            log.info("New session: " + jsessionid);
         }
      }
   }
}

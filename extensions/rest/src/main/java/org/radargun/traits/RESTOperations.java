package org.radargun.traits;

import java.util.List;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.radargun.Operation;

/**
 * @author Martin Gencur
 */
@Trait(doc = "Http operations.")
public interface RESTOperations {
   String TRAIT = RESTOperations.class.getSimpleName();

   Operation GET = Operation.register(TRAIT + ".Get");

   RESTOperationInvoker getRESTInvoker(String contextPath);

   interface RESTOperationInvoker {
      Response get(List<Cookie> cookies, MultivaluedMap<String, Object> headers);
   }
}

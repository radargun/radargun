package org.radargun.http.service;

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.radargun.WrappedHttpResponse;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.RESTOperations;

/**
 *
 * @author Martin Gencur
 */
public class RESTEasyOperations implements RESTOperations {
   private static final Log log = LogFactory.getLog(RESTEasyOperations.class);
   private final RESTEasyService service;

   public RESTEasyOperations(RESTEasyService service) {
      this.service = service;
   }

   public RESTOperationInvoker getRESTInvoker() {
      if (service.isRunning()) {
         return new HTTPOperationInvokerImpl();
      }
      return null;
   }

   protected class HTTPOperationInvokerImpl implements RESTOperationInvoker {

      private String uri;

      public HTTPOperationInvokerImpl() {
         this.uri = buildApplicationUrl();
      }

      @Override
      public WrappedHttpResponse<String> get(List<Cookie> cookiesToPass) {
         String returnedBody = null;
         Map<String, NewCookie> returnedCookies = null;
         MultivaluedMap<String,Object> returnedHeaders = null;
         if (service.isRunning()) {
            Response response = null;
            try {
               Invocation.Builder requestBuilder = service.getHttpClient().target(uri).request();
               for (Cookie cookie : cookiesToPass) {
                  requestBuilder.cookie(cookie);
               }
               Invocation get = requestBuilder.accept(service.getContentType())
                  .buildGet();
               response = get.invoke();
               if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                  log.warn("The requested URI does not exist");
               } else {
                  returnedCookies = response.getCookies();
                  returnedBody = response.readEntity(String.class);
               }
            } catch (Exception e) {
               throw new RuntimeException("RESTEasyOperations::get request threw exception: " + uri, e);
            } finally {
               if (response != null) {
                  response.close();
               }
            }
         }
         return new WrappedHttpResponse<String>(returnedCookies, returnedHeaders, returnedBody);
      }

      private String buildApplicationUrl() {
         InetSocketAddress node = service.nextServer();
         StringBuilder s = new StringBuilder("http://");
         if (service.getUsername() != null) {
            try {
               s.append(URLEncoder.encode(service.getUsername(), "UTF-8")).append(":")
                     .append(URLEncoder.encode(service.getPassword(), "UTF-8")).append("@");
            } catch (UnsupportedEncodingException e) {
               throw new RuntimeException("Could not encode the supplied username and password", e);
            }
         }
         s.append(node.getHostName()).append(":").append(node.getPort()).append("/");
         s.append(service.getContextPath());
         log.info("buildApplicationUrl = " + s.toString());
         return s.toString();
      }
   }
}

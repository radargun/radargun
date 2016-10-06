package org.radargun;

import java.util.Map;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;

public class WrappedHttpResponse<W> {
   private W body;
   private Map<String, NewCookie> cookies;
   private MultivaluedMap<String, Object> headers;

   public WrappedHttpResponse(Map<String, NewCookie> cookies, MultivaluedMap<String, Object> headers, W value) {
      super();
      this.body = value;
      this.cookies = cookies;
      this.headers = headers;
   }

   public W getBody() {
      return body;
   }

   public Map<String, NewCookie> getCookies() {
      return cookies;
   }

   public MultivaluedMap<String, Object> getHeaders() {
      return headers;
   }
}
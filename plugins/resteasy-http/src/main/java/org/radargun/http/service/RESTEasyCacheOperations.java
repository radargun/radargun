package org.radargun.http.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.BasicOperations;

/**
 * Implementation of {@link BasicOperations} through the HTTP protocol, using the JAX-RS 2.0 client
 * api with RESTEasy implementation.
 *
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public class RESTEasyCacheOperations implements BasicOperations {
   private static final Log log = LogFactory.getLog(RESTEasyCacheOperations.class);
   private final RESTEasyCacheService service;

   public RESTEasyCacheOperations(RESTEasyCacheService service) {
      this.service = service;
   }

   @Override
   public <K, V> HTTPCache<K, V> getCache(String cacheName) {
      if (service.isRunning()) {
         if (cacheName != null && (service.cacheName == null || !service.cacheName.equals(cacheName))) {
            throw new UnsupportedOperationException();
         }
         return new HTTPCache<K, V>();
      }
      return null;
   }

   protected class HTTPCache<K, V> implements BasicOperations.Cache<K, V> {

      @Override
      public V get(K key) {
         return getInternal(key).value;
      }

      private WrappedValue<V> getInternal(K key) {
         V value = null;
         EntityTag eTag = null;
         if (service.isRunning()) {
            String target = service.buildUrl(key);
            Response response = null;
            try {
               Invocation get = service.getHttpClient().target(target).request().accept(service.getContentType())
                     .buildGet();
               response = get.invoke();
               if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                  log.warn("RESTEasyCacheOperations.getInternal::Key: " + key + " does not exist in cache: "
                        + service.cacheName);
               } else {
                  eTag = response.getEntityTag();
                  value = (V) service.decodeByteArray(response.readEntity(byte[].class));
               }
            } catch (Exception e) {
               throw new RuntimeException("RESTEasyCacheOperations::get request threw exception: " + target, e);
            } finally {
               if (response != null) {
                  response.close();
               }
            }
         }
         return new WrappedValue<V>(eTag, value);
      }

      private class WrappedValue<W> {
         EntityTag eTag;
         W value;

         public WrappedValue(EntityTag eTag, W value) {
            super();
            this.eTag = eTag;
            this.value = value;
         }
      }

      @Override
      public boolean containsKey(K key) {
         if (service.isRunning()) {
            String target = service.buildUrl(key);
            Response response = null;
            response = service.getHttpClient().target(target).request().build(HttpMethod.HEAD).invoke();
            if (response.getStatus() == Response.Status.OK.getStatusCode()) {
               return true;
            }
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
               return false;
            }
            throw new RuntimeException("RESTEasyCacheOperations.containsKey::Unexpected HttpStatus: "
                  + response.getStatus() + " " + response.getStatusInfo().getReasonPhrase() + " for request " + target);
         }
         return false;
      }

      @Override
      public void put(K key, V value) {
         putInternal(key, value, null);
      }

      private void putInternal(K key, V value, EntityTag eTag) {
         if (service.isRunning()) {
            Response response = null;
            try {
               String target = service.buildUrl(key);
               Invocation.Builder putBuilder = service.getHttpClient().target(target).request().accept(service.getContentType());
               if (eTag != null) {
                  // If the eTag doesn't match the current value for the key, then the put will fail
                  putBuilder = putBuilder.header(HttpHeaders.IF_MATCH, eTag.getValue());
               }
               response = putBuilder.buildPut(Entity.entity(encodeObject(value), service.getContentType())).invoke();
               int status = response.getStatus();
               String reason = response.getStatusInfo().getReasonPhrase();
               response.close();
               if (status != Response.Status.OK.getStatusCode() && status != Response.Status.CREATED.getStatusCode()
                     && status != Response.Status.NO_CONTENT.getStatusCode()) {
                  throw new RuntimeException("RESTEasyCacheOperations.put::Unexpected HttpStatus: " + status + " "
                        + reason + " for request " + target);
               }
            } catch (IOException e) {
               throw new RuntimeException(e);
            } finally {
               if (response != null) {
                  response.close();
               }
            }
         }
      }

      @Override
      public V getAndPut(K key, V value) {
         V prevValue = null;
         EntityTag eTag = null;
         if (service.isRunning()) {
            if (containsKey(key)) {
               HTTPCache<K, V>.WrappedValue<V> wrap = getInternal(key);
               eTag = wrap.eTag;
               prevValue = wrap.value;
            }
            if (eTag == null) {
               put(key, value);
            } else {
               putInternal(key, value, eTag);
            }
         }
         return prevValue;
      }

      @Override
      public boolean remove(K key) {
         return doDelete(service.buildUrl(key), null);
      }

      public boolean remove(K key, EntityTag eTag) {
         return doDelete(service.buildUrl(key), eTag);
      }

      @Override
      public V getAndRemove(K key) {
         if (service.isRunning()) {
            if (containsKey(key)) {
               HTTPCache<K, V>.WrappedValue<V> wrap = getInternal(key);
               if (wrap.eTag != null) {
                  if (remove(key, wrap.eTag)) {
                     return wrap.value;
                  }
               } else {
                  if (remove(key)) {
                     return wrap.value;
                  }
               }
            }
         }
         return null;
      }

      @Override
      public void clear() {
         doDelete(service.buildCacheUrl(service.cacheName), null);
      }

      private boolean doDelete(String target, EntityTag eTag) {
         if (service.isRunning()) {
            Invocation.Builder deleteBuilder = service.getHttpClient().target(target).request().accept(service.getContentType());
            if (eTag != null) {
               // If the eTag doesn't match the current value for the key, then the delete will fail
               deleteBuilder = deleteBuilder.header(HttpHeaders.IF_MATCH, eTag.getValue());
            }
            Response response = deleteBuilder.buildDelete().invoke();
            int status = response.getStatus();
            String reason = response.getStatusInfo().getReasonPhrase();
            response.close();
            if (status == Response.Status.OK.getStatusCode() || status == Response.Status.NO_CONTENT.getStatusCode()) {
               return true;
            }
            if (status == Response.Status.NOT_FOUND.getStatusCode()) {
               return false;
            }
            throw new RuntimeException("RESTEasyCacheOperations.doDelete::Unexpected HttpStatus: " + status + " "
                  + reason + " for request " + target);
         }
         return false;
      }

      private byte[] encodeObject(Object object) throws IOException {
         ByteArrayOutputStream bout = new ByteArrayOutputStream();
         ObjectOutputStream oo = new ObjectOutputStream(bout);
         oo.writeObject(object);
         oo.flush();
         return bout.toByteArray();
      }

   }
}

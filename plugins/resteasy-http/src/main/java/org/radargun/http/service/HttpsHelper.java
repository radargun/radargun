package org.radargun.http.service;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class HttpsHelper {

   private HttpsHelper() {

   }

   public static void trustAll() {
      try {
         TrustManager[] noopTrustManager = new TrustManager[]{
            new X509TrustManager() {

               @Override
               public X509Certificate[] getAcceptedIssuers() {
                  return null;
               }

               @Override
               public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
               }

               @Override
               public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
               }
            }
         };

         SSLContext sc = SSLContext.getInstance("SSL");
         sc.init(null, noopTrustManager, new java.security.SecureRandom());

         HostnameVerifier validHosts = new HostnameVerifier() {
            @Override
            public boolean verify(String arg0, SSLSession arg1) {
               return true;
            }
         };

         HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
         HttpsURLConnection.setDefaultHostnameVerifier(validHosts);

      } catch (NoSuchAlgorithmException | KeyManagementException e) {
         throw new IllegalStateException("Cannot trust all certificates", e);
      }
   }
}

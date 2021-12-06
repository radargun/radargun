package org.radargun.stages;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.state.WorkerState;

@Stage(doc = "Allows to invoke Http methods.")
public class HttpInvocationStage extends AbstractDistStage {

   public enum AuthenticationMechanism {
      BASIC
   }

   @Property(optional = false, doc = "Url")
   private String url;

   @Property(optional = true, doc = "Authentication Mechanism")
   private AuthenticationMechanism authMechanism;

   @Property(optional = true, doc = "Expected Http Response body")
   private String expectedResult;

   @Property(doc = "Print response. Default false")
   private boolean printResponse;

   @Property(optional = true, doc = "Method for the URL request. Default GET")
   private String requestMethod = "GET";

   @Override
   public DistStageAck executeOnWorker() {
      String result;
      try {
         URL url = new URL(this.url);
         HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
         urlConnection.setRequestMethod(requestMethod.toUpperCase());
         if (url.getUserInfo() != null) {
            if (AuthenticationMechanism.BASIC.equals(authMechanism)) {
               String userEncoded = Base64.getEncoder().encodeToString(url.getUserInfo().getBytes());
               urlConnection.setRequestProperty("Authorization", "Basic " + userEncoded);
            }
         }
         try (InputStreamReader isr = new InputStreamReader(urlConnection.getInputStream())) {
            int numCharsRead;
            char[] charArray = new char[1024];
            StringBuffer sb = new StringBuffer();
            while ((numCharsRead = isr.read(charArray)) > 0) {
               sb.append(charArray, 0, numCharsRead);
            }
            result = sb.toString();
            if (printResponse) {
               log.info(result);
            }
         }
      } catch (MalformedURLException e) {
         return errorResponse("Malformed URL", e);
      } catch (IOException e) {
         return errorResponse(e.getMessage(), e);
      }
      return new HttpInvocationAck(workerState, result);
   }

   @Override
   public StageResult processAckOnMain(List<DistStageAck> acks) {
      StageResult stageResult = super.processAckOnMain(acks);
      if (stageResult.isError()) {
         return stageResult;
      }
      String resultFromWorker;
      if (expectedResult != null && !expectedResult.equals((resultFromWorker = getResultFromWorker(acks)))) {
         log.error(String.format("The value '%s' returned by operation is not the expected value '%s'.", resultFromWorker, expectedResult));
         return StageResult.FAIL;
      }
      return StageResult.SUCCESS;
   }

   private String getResultFromWorker(List<DistStageAck> acks) {
      for (DistStageAck dack : acks) {
         if (dack instanceof HttpInvocationAck) {
            HttpInvocationAck ack = (HttpInvocationAck) dack;
            return ack.result;
         }
      }
      return null;
   }

   private static class HttpInvocationAck extends DistStageAck {

      private final String result;

      public HttpInvocationAck(WorkerState workerState, String result) {
         super(workerState);
         this.result = result;
      }
   }
}

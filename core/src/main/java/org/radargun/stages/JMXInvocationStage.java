package org.radargun.stages;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.management.Attribute;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.state.SlaveState;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.JmxConnectionProvider;
import org.radargun.utils.PrimitiveValue;

/**
 * See example configurations in the benchmark-xsite-jmx.xml file.
 *
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
@Stage(doc = "Allows to invoke JMX-exposed methods and attributes.")
public class JMXInvocationStage extends AbstractDistStage {

   @Property(optional = false, doc = "Method will be invoked on all ObjectInstances matching given query.")
   private String query;

   @Property(optional = false, doc = "Name of the method to be invoked / attribute to be queried for.")
   private String targetName;

   @Property(doc = "Type of action to be performed. Invocation of specified method (INVOKE_METHOD) is performed "
      + "by default. Optionally, query for a specified attribute (via method-parameters) can be performed "
      + "(GET_ATTRIBUTE_VALUE) or setting a specified attribute (via method-parameters) can be performed"
      + "(SET_ATTRIBUTE_VALUE).")
   private OperationType operationType = OperationType.INVOKE_METHOD;

   @Property(doc = "Method parameters. If specified, the number of parameters must match the number of parameter "
      + "signatures supplied.", complexConverter = PrimitiveValue.ListConverter.class)
   private List<PrimitiveValue> methodParameters = new ArrayList<>();

   @Property(doc = "Method parameter signatures.")
   private String[] methodSignatures = new String[0];

   @Property(doc = "Continue method invocations if an exception occurs. Default is false.")
   private boolean continueOnFailure = false;

   @Property(doc = "Expected result value. If specified, results of method invocations are compared with this value.",
      complexConverter = PrimitiveValue.ObjectConverter.class)
   private PrimitiveValue expectedSlaveResult;

   @Property(doc = "Expected result, calculated as sum/concatenation (with ',' delimeter) of results from individual slaves.",
      complexConverter = PrimitiveValue.ObjectConverter.class)
   private PrimitiveValue expectedTotalResult;

   @InjectTrait
   private JmxConnectionProvider jmxConnectionProvider;

   @Override
   public DistStageAck executeOnSlave() {
      if (!isServiceRunning()) {
         return successfulResponse();
      }
      if (methodParameters.size() != methodSignatures.length) {
         return errorResponse("Signatures need to be specified for individual method parameters.");
      }
      MBeanServerConnection connection = null;
      if (jmxConnectionProvider != null) {
         JMXConnector connector = jmxConnectionProvider.getConnector();
         if (connector != null) {
            try {
               connection = connector.getMBeanServerConnection();
            } catch (IOException e) {
               return errorResponse("Failed to connect to MBean server.", e);
            }
         } else {
            return errorResponse("Failed to connect to MBean server.");
         }
      } else {
         connection = ManagementFactory.getPlatformMBeanServer();
      }
      Collection<ObjectInstance> queryResult = null;
      try {
         queryResult = getQueryResult(connection);
         if (queryResult == null || queryResult.isEmpty()) {
            return errorResponse(String.format("Specified query '%s' returned no results.", query));
         }
         log.trace(String.format("Query returned %d results", queryResult.size()));
      } catch (Exception e) {
         return errorResponse(String.format("Exception while performing query '%s'", query), e);
      }
      List<Object> values = new ArrayList<>();
      if (methodParameters != null) {
         for (PrimitiveValue primitiveValue : methodParameters) {
            values.add(primitiveValue.getElementValue());
         }
      }
      List<Object> results = new ArrayList<>(queryResult.size());
      for (ObjectInstance objectInstance : queryResult) {
         try {
            Object result = null;
            if (operationType == OperationType.INVOKE_METHOD) {
               log.trace("Invoking method " + targetName);
               result = connection.invoke(objectInstance.getObjectName(), targetName, values.toArray(new Object[methodParameters.size()]), methodSignatures);
            } else if (operationType == OperationType.GET_ATTRIBUTE_VALUE) {
               log.trace("Getting value of attribute " + targetName);
               result = connection.getAttribute(objectInstance.getObjectName(), targetName);
            } else if (operationType == OperationType.SET_ATTRIBUTE_VALUE) {
               if (methodParameters != null && !methodParameters.isEmpty()) {
                  log.trace("Setting value of attribute " + targetName + " to "
                     + methodParameters.get(0).getElementValue().toString());
                  Attribute attribute = new Attribute(targetName, methodParameters.get(0).getElementValue());
                  connection.setAttribute(objectInstance.getObjectName(), attribute);
                  result = connection.getAttribute(objectInstance.getObjectName(), targetName);
               } else {
                  return errorResponse("New value for attribute was not specified in methodParameters property.");
               }
            }
            if (expectedSlaveResult != null && !expectedSlaveResult.getElementValue().equals(result)) {
               return errorResponse(String.format("Method invocation returned incorrect result. Expected '%s', was '%s'.", expectedSlaveResult.getElementValue(), result));
            }
            results.add(result);
         } catch (Exception e) {
            if (continueOnFailure) {
               continue;
            } else {
               return errorResponse(String.format("Exception while invoking method '%s'.", targetName), e);
            }
         }
      }
      return new JMXInvocationAck(slaveState, results);
   }

   private Collection<ObjectInstance> getQueryResult(MBeanServerConnection connection) throws MalformedObjectNameException, IOException {
      return connection.queryMBeans(new ObjectName(query), null);
   }


   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult stageResult = super.processAckOnMaster(acks);
      if (stageResult.isError()) {
         return stageResult;
      }
      if (expectedTotalResult != null) {
         if (acks == null || acks.isEmpty()) {
            log.error("Expected total result has been specified, but no results have been returned from slaves.");
            return StageResult.FAIL;
         }
         Object totalResult = null;
         for (DistStageAck dack : acks) {
            if (dack instanceof JMXInvocationAck) {
               JMXInvocationAck ack = (JMXInvocationAck) dack;
               for (Object ackResult : ack.results) {
                  try {
                     if (totalResult == null) {
                        totalResult = ackResult;
                     } else {
                        log.trace(String.format("Adding value %s, current total value %s", ackResult.toString(), totalResult.toString()));
                        if (!totalResult.getClass().equals(ackResult.getClass())) {
                           throw new IllegalStateException(String.format("Failed to determine total result due to incompatible value." +
                              " Expected %s, got %s.", totalResult.getClass().getName(), ackResult.getClass().getName()));
                        }
                        if (ackResult instanceof Long) {
                           totalResult = (Long) totalResult + (Long) ackResult;
                        } else if (ackResult instanceof Integer) {
                           totalResult = (Integer) totalResult + (Integer) ackResult;
                        } else if (ackResult instanceof Byte) {
                           totalResult = (Byte) totalResult + (Byte) ackResult;
                        } else if (ackResult instanceof Short) {
                           totalResult = (Short) totalResult + (Short) ackResult;
                        } else if (ackResult instanceof Boolean) {
                           totalResult = (Boolean) totalResult && (Boolean) ackResult;
                        } else if (ackResult instanceof Character) {
                           totalResult = totalResult + "," + ackResult;
                        } else if (ackResult instanceof String) {
                           totalResult = totalResult + "," + ackResult;
                        } else {
                           throw new IllegalStateException(String.format("Values of type %s are not supported.", totalResult.getClass().getName()));
                        }
                     }
                  } catch (Exception e) {
                     log.error("Failed to determine total result. Incompatible value " + ackResult, e);
                  }
               }
            }
         }
         if (totalResult == null) {
            log.error("Total result is empty.");
            return StageResult.FAIL;
         }
         if (totalResult.equals(expectedTotalResult.getElementValue())) {
            log.trace("Total value is the one expected " + expectedTotalResult.getElementValue());
            return StageResult.SUCCESS;
         } else {
            log.error(String.format("Total value %s is not the one expected %s.",
               totalResult.toString(), expectedTotalResult.getElementValue().toString()));
            return StageResult.FAIL;
         }
      } else {
         return StageResult.SUCCESS;
      }
   }

   private static enum OperationType {
      INVOKE_METHOD, GET_ATTRIBUTE_VALUE, SET_ATTRIBUTE_VALUE
   }

   private static class JMXInvocationAck extends DistStageAck {

      private final List<Object> results;

      public JMXInvocationAck(SlaveState slaveState, List<Object> results) {
         super(slaveState);
         this.results = results;
      }
   }
}

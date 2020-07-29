package org.radargun.traits;

@Trait(doc = "Provides basic access to the failure service.")
public interface Failure {

   void createFailure(String action);

   void solveFailure(String action);

   boolean checkIfFailurePresent(String action, Object expectedValue);
}

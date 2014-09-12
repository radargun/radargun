package org.radargun.traits;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Trait(doc = "Information about clustered nodes.")
public interface Clustered {
   /**
    * @return True if this slave has unique role in the cluster
    */
   boolean isCoordinator();

   /**
    * @return Number of nodes that currently form a cluster with this service (including this one).
    */
   int getClusteredNodes();
}

package org.radargun;

import java.io.Serializable;

/**
 * Asck that is sent from each slave to the master containing the result of the slave's processing for a stage.
 *
 * @author Mircea.Markus@jboss.com
 */
public interface DistStageAck extends Serializable {

   public int getSlaveIndex();

   public long getDuration();

   public void setDuration(long duration);
}

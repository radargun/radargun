package org.radargun.reporting;

import java.util.Collection;

/**
 * It is expected that the implementation of this class will use @Property annotations to fill in the properties.
 */
public interface Reporter {
   void run(Collection<Report> reports);
}

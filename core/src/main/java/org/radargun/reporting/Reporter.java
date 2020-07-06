package org.radargun.reporting;

import java.util.Collection;

import org.radargun.config.MainConfig;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 *
 * It is expected that the implementation of this class will use @Property annotations to fill in the properties.
 */
public interface Reporter {

   boolean run(MainConfig mainConfig, Collection<Report> reports, int returnCode) throws Exception;
}

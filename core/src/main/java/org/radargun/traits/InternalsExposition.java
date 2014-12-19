package org.radargun.traits;

import java.util.Map;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Trait(doc = "Provides service-dependent internal data that should be fed into Timeline")
public interface InternalsExposition {
   Map<String, Number> getValues();
}

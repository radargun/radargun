package org.radargun.traits;

import java.util.Map;

@Trait(doc = "Provides service-dependent internal data.")
public interface InternalsExposition {
   /**
    * @return data that should be fed into Timeline
    */
   Map<String, Number> getValues();

   /**
    * Retrieve custom statistical data.
    * @param type
    * @return
    */
   String getCustomStatistics(String type);

   /**
    * Reset internal counters with custom statistical data.
    * @param type
    */
   void resetCustomStatistics(String type);
}

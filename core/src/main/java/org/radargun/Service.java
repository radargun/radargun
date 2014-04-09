package org.radargun;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks types that can be instantiated as a service
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Service {

   String SLAVE_INDEX = "slaveIndex";
   String FILE = "file";
   String CONFIG_NAME = "configName";
   String PLUGIN = "plugin";

   String doc();
}

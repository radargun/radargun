package org.radargun.config;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The contents of this attribute are written into the XML schema.
 *
 * @author rvansa
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Documentation {
   String value();
}

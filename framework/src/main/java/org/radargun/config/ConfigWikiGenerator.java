/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.radargun.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

import org.radargun.Stage;

/**
 * This is used to generate the stages/properties with GitHub wiki markup
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class ConfigWikiGenerator {
   public static void main(String[] args) {
      for (Map.Entry<String, Class<? extends Stage>> entry : StageHelper.getStagesFromJar(args[0]).entrySet()) {
         if (Modifier.isAbstract(entry.getValue().getModifiers())) continue;
         System.out.println("## " + entry.getKey());
         System.out.println(entry.getValue().getAnnotation(org.radargun.config.Stage.class).doc());
         for (Map.Entry<String, Field> property : PropertyHelper.getProperties(entry.getValue()).entrySet()) {
            Property propertyAnnotation = property.getValue().getAnnotation(Property.class);
            if (property.getKey().equals(propertyAnnotation.deprecatedName()) || propertyAnnotation.readonly()) continue;
            System.out.println("* " + property.getKey()
                                     + (propertyAnnotation.optional() ? " [optional]" : " [mandatory]") + ": " + propertyAnnotation.doc());
         }
         System.out.println();
      }
   }
}

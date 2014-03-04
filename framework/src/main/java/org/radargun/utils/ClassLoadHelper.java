/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.radargun.utils;

import java.net.URLClassLoader;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.state.StateBase;

public class ClassLoadHelper {

   private static final String PREVIOUS_PLUGIN = "__PREVIOUS_PLUGIN__";
   private static final String CLASS_LOADER = "__CLASS_LOADER__";
   private static final Log log = LogFactory.getLog(ClassLoadHelper.class);
   
   private boolean useSmartClassLoading;
   private Class<?> instantiator;
   private String plugin;
   private StateBase state;

   public ClassLoadHelper(boolean useSmartClassLoading, Class<?> instantiator, String plugin, StateBase state) {
      this.useSmartClassLoading = useSmartClassLoading;
      this.instantiator = instantiator;
      this.plugin = plugin;
      this.state = state;
   }

   public Object createInstance(String classFqn) throws Exception {
      if (!useSmartClassLoading) {
         return Class.forName(classFqn).newInstance();
      }
      ClassLoader classLoader = getLoader();
      log.info("Creating newInstance " + classFqn + " with classloader " + classLoader);
      return classLoader.loadClass(classFqn).newInstance();
   }

   public ClassLoader getLoader() {
      String prevProduct = (String) state.get(PREVIOUS_PLUGIN);
      if (prevProduct == null || !prevProduct.equals(plugin)) {
         URLClassLoader classLoader = Utils.buildPluginSpecificClassLoader(plugin, instantiator.getClassLoader());
         state.put(CLASS_LOADER, classLoader);
         state.put(PREVIOUS_PLUGIN, plugin);
         return classLoader;
      } else {//same product and there is a class loader
         return (URLClassLoader) state.get(CLASS_LOADER);
      }
   }
}

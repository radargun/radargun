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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.state.StateBase;

public class ClassLoadHelper {
   
   private static final Log log = LogFactory.getLog(ClassLoadHelper.class);
   
   private boolean useSmartClassLoading;
   private Class<?> instantiator;
   private String thisProduct;
   private StateBase state;
   private String prevProductKey;
   private String classLoaderKey;
   
   public ClassLoadHelper(boolean useSmartClassLoading, Class<?> instantiator, String thisProduct, StateBase state,
         String prevProductKey, String classLoaderKey) {      
      this.useSmartClassLoading = useSmartClassLoading;
      this.instantiator = instantiator;
      this.thisProduct = thisProduct;
      this.state = state;
      this.prevProductKey = prevProductKey;
      this.classLoaderKey = classLoaderKey;
   }

   public Object createInstance(String classFqn) throws Exception {
      if (!useSmartClassLoading) {
         return Class.forName(classFqn).newInstance();
      }
      URLClassLoader classLoader;
      String prevProduct = (String) state.get(prevProductKey);
      if (prevProduct == null || !prevProduct.equals(thisProduct)) {
         classLoader = Utils.buildProductSpecificClassLoader(thisProduct, instantiator.getClassLoader());
         state.put(classLoaderKey, classLoader);
         state.put(prevProductKey, thisProduct);
      } else {//same product and there is a class loader
         classLoader = (URLClassLoader) state.get(classLoaderKey);
      }
      log.info("Creating newInstance " + classFqn + " with classloader " + classLoader);
      Thread.currentThread().setContextClassLoader(classLoader);
      return classLoader.loadClass(classFqn).newInstance();
   }
}

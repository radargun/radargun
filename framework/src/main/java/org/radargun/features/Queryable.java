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
package org.radargun.features;

import java.util.List;
import java.util.Map;

/**
 * Allows running queries on the cache.
 *
 * @author Anna Manukyan
 */
public interface Queryable {

   /**
    * The parameter name, which value is the field name which should be queried.
    */
   public static final String QUERYABLE_FIELD = "onField";

   /**
    * The parameter name, which value is the string to which the specified field should match.
    */
   public static final String MATCH_STRING = "matching";

   /**
    * The parameter name, which value specifies whether the wildcard query should be executed or keyword.
    */
   public static final String IS_WILDCARD = "wildcard";

   /**
    * Executes keyword or wildcard queries based on the passed parameters.
    * @param queryParameters        the map, which contains the queryable field name with it's expected value.
    */
   public QueryResult executeQuery(Map<String, Object> queryParameters);

   public interface QueryResult {
      public int size();

      public List list();
   }
}

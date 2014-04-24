package org.radargun.traits;

import java.util.List;
import java.util.Map;

import org.radargun.Operation;

/**
 * @author Anna Manukyan
 */
@Trait(doc = "Allows running queries on the node.")
public interface Queryable {
   Operation QUERY = Operation.register(Queryable.class.getSimpleName() + ".Query");

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

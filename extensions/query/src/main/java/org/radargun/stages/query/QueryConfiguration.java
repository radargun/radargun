package org.radargun.stages.query;

import java.util.List;

import org.radargun.config.Property;

/**
 *
 * The conditions field are the standard conditions that you would expect in a WHERE clause.
 * Having refers to the conditions which follow the GROUP BY clause, and can make use
 * of aggregations. Projection and orderBy have both alternative versions
 * (projectionAggregated, orderByAggregatedColumns) which can use aggregations, too.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class QueryConfiguration {
   @Property(name = "class", deprecatedName = "queryObjectClass", optional = false,
      doc = "Full class name of the object that should be queried. Mandatory.")
   public String clazz;

   @Property(optional = false, doc = "Conditions used in the query", complexConverter = Condition.ConditionConverter.class)
   public List<Condition> conditions;

   @Property(doc = "Conditions applied to groups when using group-by, can use aggregations.", complexConverter = Condition.ConditionConverter.class)
   protected List<Condition> having;

   @Property(doc = "Use projection instead of returning full object. Default is without projection.")
   public String[] projection;

   @Property(doc = "Projection, possibly with aggregations.", complexConverter = Condition.ProjectionConverter.class)
   protected List<Condition.SelectExpressionElement>  projectionAggregated;

   @Property(doc = "Use sorting order, in form [attribute[:(ASC|DESC)]][,attribute[:(ASC|DESC)]]*. " +
      "Without specifying ASC or DESC the sort order defaults to ASC. Default is unordereded.",
      converter = OrderBy.ListConverter.class)
   public List<OrderBy> orderBy;

   @Property(doc = "Sorting, possibly by aggregated columns.", complexConverter = Condition.AggregatedSortConverter.class)
   protected List<Condition.OrderedSelectExpressionElement> orderByAggregatedColumns;

   @Property(doc = "Use grouping, in form [attribute][,attribute]*. Default is without grouping.")
   protected String[] groupBy;

   @Property(doc = "Offset in the results. Default is none.")
   public long offset = -1;

   @Property(doc = "Maximum number of the results. Default is none.")
   public long limit = -1;
}

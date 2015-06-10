package org.radargun.stages.query;

import org.radargun.config.Property;

import java.util.List;

/**
 *
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class QueryConfiguration {
   @Property(name = "class", deprecatedName = "queryObjectClass", optional = false,
         doc = "Full class name of the object that should be queried. Mandatory.")
   public String clazz;

   @Property(optional = false, doc = "Conditions used in the query", complexConverter = Condition.ConditionConverter.class)
   public List<Condition> conditions;

   @Property(doc = "Use projection instead of returning full object. Default is without projection.")
   public String[] projection;

   @Property(doc = "Use sorting order, in form [attribute[:(ASC|DESC)]][,attribute[:(ASC|DESC)]]*. " +
         "Without specifying ASC or DESC the sort order defaults to ASC. Default is unordereded.",
         converter = OrderBy.ListConverter.class)
   public List<OrderBy> orderBy;

   @Property(doc = "Offset in the results. Default is none.")
   public long offset = -1;

   @Property(doc = "Maximum number of the results. Default is none.")
   public long limit = -1;
}

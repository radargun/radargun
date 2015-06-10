package org.radargun.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Stack;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.utils.Tokenizer;

/**
 * Class with only static methods used for evaluation of expressions. Does:
 * - replace ${property.name} with the actual property value (from System.getProperty())
 * - replace ${property.name : default.value} with property value or default.value if the property is not defined
 * - evaluate infix expressions inside #{ expression } block, available operators are:
 *   '+' (addition), '-' (subtraction), '*' (multiplying), '/' (division), '%' (modulo operation),
 *   '..' (range generation), ',' (adding to list), '(' and ')' as usual parentheses.
 *
 * Examples:
 * #{ 1..3,5 } -> 1,2,3,5
 * #{ ( ${x} + 5 ) * 6 } with -Dx=2 -> 42
 * foo${y}bar with -Dy=goo -> foogoobar
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Evaluator {
   private final static Log log = LogFactory.getLog(Evaluator.class);

   /**
    * Parse string possibly containing expressions and properties and convert the value to integer.
    */
   public static int parseInt(String string) {
      return Integer.parseInt(parseString(string));
   }

   /**
    * Parse string possibly containing expressions and properties.
    */
   public static String parseString(String string) {
      if (string == null) return null;
      StringBuilder sb = new StringBuilder();
      int currentIndex = 0;
      while (currentIndex < string.length()) {
         int propertyIndex = string.indexOf("${", currentIndex);
         int expressionIndex = string.indexOf("#{", currentIndex);
         int nextIndex = propertyIndex < 0 ?
               (expressionIndex < 0 ? string.length() : expressionIndex) :
               (expressionIndex < 0 ? propertyIndex : Math.min(expressionIndex, propertyIndex));
         sb.append(string.substring(currentIndex, nextIndex));
         currentIndex = nextIndex + 2;
         if (nextIndex == propertyIndex) {
            nextIndex = string.indexOf('}', currentIndex);
            if (nextIndex < 0) {
               throw new IllegalArgumentException(string);
            }
            sb.append(evalProperty(string, currentIndex, nextIndex));
            currentIndex = nextIndex + 1;
         } else if (nextIndex == expressionIndex) {
            Stack<Operator> operators = new Stack<>();
            Stack<Value> operands = new Stack<>();
            Tokenizer tokenizer = new Tokenizer(string, Operator.symbols(), true, false, currentIndex);
            boolean closed = false;
            // we set this to true because if '-' is on the beginning, is interpreted as sign
            boolean lastTokenIsOperator = true;
            boolean negativeSign = false;
            while (tokenizer.hasMoreTokens()) {
               String token = tokenizer.nextToken();
               Operator op = Operator.from(token);
               if (op == null) {
                  operands.push(new Value(negativeSign ? "-" + token : token));
                  lastTokenIsOperator = false;
                  continue;
               } else if (op.isWhite()) {
                  // do not set lastTokenIsOperator
                  continue;
               } else if (op == Operator.OPENVAR) {
                  if (!tokenizer.hasMoreTokens()) throw new IllegalArgumentException(string);
                  StringBuilder var = new StringBuilder();
                  while (tokenizer.hasMoreTokens()) {
                     token = tokenizer.nextToken();
                     if ((op = Operator.from(token)) == null || op.isWhite()) {
                        var.append(token);
                     } else {
                        break;
                     }
                  }
                  if (op != Operator.CLOSEVAR) {
                     throw new IllegalArgumentException("Expected '}' but found " + token + " in " + string);
                  }
                  operands.push(evalProperty(var.toString(), 0, var.length()));
                  lastTokenIsOperator = false;
                  continue;
               } else if (op == Operator.CLOSEVAR) {
                  // end of expression to be evaluated
                  closed = true;
                  break;
               } else if (op.isFunction()) {
                  operators.push(op);
               } else if (op == Operator.OPENPAR) {
                  operators.push(op);
               } else if (op == Operator.CLOSEPAR) {
                  while ((op = operators.pop()) != Operator.OPENPAR) {
                     op.exec(operands);
                     if (operators.isEmpty()) throw new IllegalStateException("Cannot find matching '('");
                  }
                  while (!operators.isEmpty() && operators.peek().isFunction()) {
                     op = operators.pop();
                     op.exec(operands);
                  }
               } else if (op == Operator.MINUS && lastTokenIsOperator) {
                  negativeSign = true;
               } else {
                  while (true) {
                     if (operators.isEmpty() || operators.peek() == Operator.OPENPAR ||
                         operators.peek().precedence() < op.precedence()) {
                        operators.push(op);
                        break;
                     }
                     operators.pop().exec(operands);
                  }
                  lastTokenIsOperator = true;
               }
            }
            if (!closed) {
               throw new IllegalArgumentException("Expression is missing closing '}': " + string);
            }
            while (!operators.empty()) {
               operators.pop().exec(operands);
            }
            sb.append(operands.pop());
            if (!operands.empty()) {
               throw new IllegalArgumentException(operands.size() + " operands not processed: top=" + operands.pop() + " all=" + operands);
            }
            currentIndex = tokenizer.getPosition();
         }
      }
      return sb.toString();
   }


   private static Value evalProperty(String string, int startIndex, int endIndex) {
      int colonIndex = string.indexOf(':', startIndex);
      String property;
      Value value = null, def = null;
      if (colonIndex < 0 || colonIndex > endIndex) {
         property = string.substring(startIndex, endIndex).trim();
      } else {
         property = string.substring(startIndex, colonIndex).trim();
         def = new Value(string.substring(colonIndex + 1, endIndex).trim());
      }
      String strValue = System.getProperty(property);
      if (strValue != null && !strValue.isEmpty()) {
         value = new Value(strValue.trim());
      } else {
         if (property.startsWith("env.")) {
            String env = System.getenv(property.substring(4));
            if (env != null && !env.isEmpty()) {
               value = new Value(env.trim());
            }
         } else if (property.startsWith("random.")) {
            value = random(property);
         }
      }
      if (value != null) {
         return value;
      } else if (def != null) {
         return def;
      } else {
         log.debugf("Failed to resolve property ${%s}, defined properties are: ", property);
         for (Map.Entry<Object, Object> prop : System.getProperties().entrySet()) {
            log.debugf("${%s} -> '%s'", prop.getKey(), prop.getValue());
         }
         for (Map.Entry<String, String> env : System.getenv().entrySet()) {
            log.debugf("${env.%s} -> '%s'", env.getKey(), env.getValue());
         }
         throw new IllegalArgumentException("Property '" + property + "' not defined!");
      }
   }

   private static Value random(String type) {
      Random random = new Random();
      if (type.equals("random.int")) {
         return new Value(random.nextInt() & Integer.MAX_VALUE);
      } else if (type.equals("random.long")) {
         return new Value(random.nextLong() & Long.MAX_VALUE);
      } else if (type.equals("random.double")) {
         return new Value(random.nextDouble());
      } else if (type.equals("random.boolean")) {
         return new Value(String.valueOf(random.nextBoolean()));
      } else {
         return null;
      }
   }

   private static Value range(Value first, Value second) {
      if (first.type.canBeLong() && second.type.canBeLong()) {
         long from = first.getLong();
         long to = second.getLong();
         List<Value> values = new ArrayList((int) Math.abs(from - to));
         long inc = from <= to ? 1 : -1;
         for (long i = from; from <= to ? i <= to : i >= to; i += inc) values.add(new Value(i));
         return new Value(values);
      } else {
         throw new IllegalArgumentException(first + " .. " + second);
      }
   }

   private static Value multiply(Value first, Value second) {
      if (first.type.canBeLong() && second.type.canBeLong()) {
         return new Value(first.getLong() * second.getLong());
      } else if (first.type.canBeDouble() && second.type.canBeDouble()) {
         return new Value(first.getDouble() * second.getDouble());
      } else {
         throw new IllegalArgumentException(first + " * " + second);
      }
   }

   private static Value minus(Value first, Value second) {
      if (first.type.canBeLong() && second.type.canBeLong()) {
         return new Value(first.getLong() - second.getLong());
      } else if (first.type.canBeDouble() && second.type.canBeDouble()) {
         return new Value(first.getDouble() - second.getDouble());
      } else {
         throw new IllegalArgumentException(first + " - " + second);
      }
   }

   private static Value plus(Value first, Value second) {
      if (first.type.canBeLong() && second.type.canBeLong()) {
         return new Value(first.getLong() + second.getLong());
      } else if (first.type.canBeDouble() && second.type.canBeDouble()) {
         return new Value(first.getDouble() + second.getDouble());
      } else {
         throw new IllegalArgumentException(first + " + " + second);
      }
   }

   private static Value div(Value first, Value second) {
      if (first.type.canBeLong() && second.type.canBeLong()) {
         return new Value(first.getLong() / second.getLong());
      } else if (first.type.canBeDouble() && second.type.canBeDouble()) {
         return new Value(first.getDouble() / second.getDouble());
      } else {
         throw new IllegalArgumentException(first + " / " + second);
      }
   }

   private static Value modulo(Value first, Value second) {
      if (first.type.canBeLong() && second.type.canBeLong()) {
         return new Value(first.getLong() % second.getLong());
      } else {
         throw new IllegalArgumentException(first + " % " + second);
      }
   }

   private static Value power(Value first, Value second) {
      if (first.type.canBeLong() && second.type.canBeLong()) {
         long base = first.getLong();
         long power = second.getLong();
         long value = 1;
         if (power < 0) {
            return new Value(Math.pow(base, power));
         }
         for (long i = power; i > 0; --i) {
            value *= base;
         }
         return new Value(value);
      } else if (first.type.canBeDouble() && second.type.canBeDouble()) {
         return new Value(Math.pow(first.getDouble(), second.getDouble()));
      } else {
         throw new IllegalArgumentException(first + "^" + second);
      }
   }

   private static Value concat(Value first, Value second) {
      List<Value> list = new ArrayList();
      if (first.type == ValueType.LIST) {
         list.addAll(first.getList());
      } else {
         list.add(first);
      }
      if (second.type == ValueType.LIST) {
         list.addAll(second.getList());
      } else {
         list.add(second);
      }
      return new Value(list);
   }

   private static Value max(Value value) {
      if (value.type == ValueType.LIST) {
         Value max = null;
         for (Value v : value.getList()) {
            if (max == null) {
               max = v;
            } else if (max.type.canBeLong() && v.type.canBeLong()) {
               max = max.getLong() >= v.getLong() ? max : v;
            } else if (max.type.canBeDouble() && v.type.canBeDouble()) {
               max = max.getDouble() >= v.getDouble() ? max : v;
            } else {
               throw new IllegalArgumentException("max(" + value + ")");
            }
         }
         if (max == null) {
            throw new IllegalArgumentException("max of 0 values");
         }
         return max;
      } else {
         log.warn("Computing max from single value");
         return value;
      }
   }

   private static Value min(Value value) {
      if (value.type == ValueType.LIST) {
         Value min = null;
         for (Value v : value.getList()) {
            if (min == null) {
               min = v;
            } else if (min.type.canBeLong() && v.type.canBeLong()) {
               min = min.getLong() <= v.getLong() ? min : v;
            } else if (min.type.canBeDouble() && v.type.canBeDouble()) {
               min = min.getDouble() <= v.getDouble() ? min : v;
            } else {
               throw new IllegalArgumentException("min(" + value + ")");
            }
         }
         if (min == null) {
            throw new IllegalArgumentException("min of 0 values");
         }
         return min;
      } else {
         log.warn("Computing min from single value");
         return value;
      }
   }

   private static Value floor(Value value) {
      if (value.type.canBeLong()) {
         return value;
      } else if (value.type.canBeDouble()) {
         return new Value((long) Math.floor(value.getDouble()));
      } else {
         throw new IllegalArgumentException("floor(" + value + ")");
      }
   }

   private static Value ceil(Value value) {
      if (value.type.canBeLong()) {
         return value;
      } else if (value.type.canBeDouble()) {
         return new Value((long) Math.ceil(value.getDouble()));
      } else {
         throw new IllegalArgumentException("ceil(" + value + ")");
      }
   }

   private static Value abs(Value value) {
      if (value.type.canBeLong()) {
         return new Value(Math.abs(value.getLong()));
      } else if (value.type.canBeDouble()) {
         return new Value(Math.abs(value.getDouble()));
      } else {
         throw new IllegalArgumentException("abs(" + value + ")");
      }
   }

   private static Value listGet(Value first, Value second) {
      if (!second.type.canBeLong()) {
         throw new IllegalArgumentException("Invalid index argument " + second + ", natural number expected");
      }
      List<Value> valueList = ValueType.LIST == first.type ? first.getList() : convertToList(first.objectValue);
      if (valueList.size() > 1) {
         if (valueList.size() <= second.getLong()) {
            throw new IllegalArgumentException("Out of bounds index value " + second.getLong() + ", list size " + valueList.size());
         }
         return new Value(valueList.get((int) second.getLong()).toString());
      } else {
         if (second.getLong() != 0) {
            throw new IllegalArgumentException("Out of bounds index value " + second.getLong() + ", list size " + 1);
         }
         return new Value(first.objectValue.toString());
      }
   }

   private static Value listSize(Value first) {
      if (first.type.canBeLong) {
         return new Value(1);
      } else {
         int length = first.objectValue.toString().split(",").length;
         if (length == 0) {
            throw new IllegalArgumentException("Invalid argument " + first + " provided, list value expected");
         }
         return new Value(length);
      }
   }

   private static List<Value> convertToList(Object value) {
      String[] strings = value.toString().split(",");
      List<Value> valueList = new ArrayList<>(strings.length);
      for (String string : strings) {
         valueList.add(new Value(string.trim()));
      }
      return valueList;
   }

   protected static Value toDouble(Value value) {
      if (value.type.canBeDouble()) return new Value(value.getDouble());
      throw new IllegalArgumentException(value.toString());
   }

   private static Value gcd(Value value) {
      if (value.type != ValueType.LIST) throw new IllegalArgumentException(value.toString());
      ArrayList<Long> list = new ArrayList<>();
      for (Value v : value.getList()) {
         if (!v.type.canBeLong) throw new IllegalArgumentException(v.toString() + " in gcd " + value.toString());
         list.add(Math.abs(v.getLong()));
      }
      if (list.isEmpty()) throw new IllegalStateException();
      long a = list.get(0);
      for (int index = 1; index < list.size(); ++index) {
         long b = Math.abs(list.get(index));
         if (b > a) {
            long tmp = a;
            a = b;
            b = tmp;
         }
         for (; ; ) {
            if (b == 0) break;
            long tmp = b;
            b = a % b;
            a = tmp;
         }
      }
      return new Value(a);
   }

   private enum ValueType {
      STRING(false, false),
      LONG(true, true),
      DOUBLE(false, true),
      LIST(false, false);

      private final boolean canBeLong;
      private final boolean canBeDouble;

      ValueType(boolean canBeLong, boolean canBeDouble) {
         this.canBeLong = canBeLong;
         this.canBeDouble = canBeDouble;
      }

      public boolean canBeLong() {
         return canBeLong;
      }

      public boolean canBeDouble() {
         return canBeDouble;
      }
   }

   private static class Value {
      public final ValueType type;
      private final double doubleValue;
      private final long longValue;
      private final Object objectValue;

      private Value(long longValue) {
         this.type = ValueType.LONG;
         this.doubleValue = longValue;
         this.longValue = longValue;
         this.objectValue = String.valueOf(longValue);
      }

      private Value(double doubleValue) {
         this.type = ValueType.DOUBLE;
         this.doubleValue = doubleValue;
         this.longValue = 0;
         this.objectValue = String.valueOf(doubleValue);
      }

      private Value(String string) {
         ValueType t = ValueType.STRING;
         double d = Double.NaN;
         long l = 0;
         Object o = string;
         try {
            d = l = Long.parseLong(string);
            o = l;
            t = ValueType.LONG;
         } catch (NumberFormatException e) {
            try {
               o = d = Double.parseDouble(string);
               t = ValueType.DOUBLE;
            } catch (NumberFormatException e2) {
            }
         }
         type = t;
         doubleValue = d;
         longValue = l;
         objectValue = o;
      }

      public Value(List<Value> values) {
         type = ValueType.LIST;
         doubleValue = Double.NaN;
         longValue = 0;
         objectValue = values;
      }

      public long getLong() {
         return longValue;
      }

      public double getDouble() {
         return doubleValue;
      }

      public List<Value> getList() {
         return (List<Value>) objectValue;
      }

      @Override
      public String toString() {
         if (type == ValueType.LIST) {
            StringBuilder sb = new StringBuilder();
            for (Value v : (List<Value>) objectValue) {
               if (sb.length() != 0) sb.append(", ");
               // inner lists would require special treatment
               if (v.type == ValueType.LIST) {
                  sb.append("[").append(v).append("]");
               } else {
                  sb.append(v);
               }
            }
            return sb.toString();
         } else {
            return String.valueOf(objectValue);
         }
      }
   }

   private static interface OneArgFunctor {
      Value exec(Value value);
   }

   private static interface TwoArgFunctor {
      Value exec(Value first, Value second);
   }

   private enum Operator {
      SPACE(" ", 0, true, false, null, null),
      TAB("\t", 0, true, false, null, null),
      NEWLINE("\n", 0, true, false, null, null),
      CR("\r", 0, true, false, null, null),
      PLUS("+", 100, false, false, null, new TwoArgFunctor() {
         @Override
         public Value exec(Value first, Value second) {
            return plus(first, second);
         }
      }),
      MINUS("-", 100, false, false, null, new TwoArgFunctor() {
         @Override
         public Value exec(Value first, Value second) {
            return minus(first, second);
         }
      }),
      MULTIPLY("*", 200, false, false, null, new TwoArgFunctor() {
         @Override
         public Value exec(Value first, Value second) {
            return multiply(first, second);
         }
      }),
      DIVIDE("/", 200, false, false, null, new TwoArgFunctor() {
         @Override
         public Value exec(Value first, Value second) {
            return div(first, second);
         }
      }),
      MODULO("%", 200, false, false, null, new TwoArgFunctor() {
         @Override
         public Value exec(Value first, Value second) {
            return modulo(first, second);
         }
      }),
      POWER("^", 300, false, false, null, new TwoArgFunctor() {
         @Override
         public Value exec(Value first, Value second) {
            return power(first, second);
         }
      }),
      RANGE("..", 50, false, false, null, new TwoArgFunctor() {
         @Override
         public Value exec(Value first, Value second) {
            return range(first, second);
         }
      }),
      COMMA(",", 10, false, false, null, new TwoArgFunctor() {
         @Override
         public Value exec(Value first, Value second) {
            return concat(first, second);
         }
      }),
      OPENPAR("(", 0, false, false, null, null),
      CLOSEPAR(")", 0, false, false, null, null),
      OPENVAR("${", 0, false, false, null, null),
      CLOSEVAR("}", 0, false, false, null, null),
      MAX("max", 0, false, true, new OneArgFunctor() {
         @Override
         public Value exec(Value value) {
            return max(value);
         }
      }, null),
      MIN("min", 0, false, true, new OneArgFunctor() {
         @Override
         public Value exec(Value value) {
            return min(value);
         }
      }, null),
      FLOOR("floor", 0, false, true, new OneArgFunctor() {
         @Override
         public Value exec(Value value) {
            return floor(value);
         }
      }, null),
      CEIL("ceil", 0, false, true, new OneArgFunctor() {
         @Override
         public Value exec(Value value) {
            return ceil(value);
         }
      }, null),
      ABS("abs", 0, false, true, new OneArgFunctor() {
         @Override
         public Value exec(Value value) {
            return abs(value);
         }
      }, null),
      LIST_GET(".get", 400, false, true, null, new TwoArgFunctor() {
         @Override
         public Value exec(Value first, Value second) {
            return listGet(first, second);
         }
      }),
      LIST_SIZE(".size", 500, false, true, new OneArgFunctor() {
         @Override
         public Value exec(Value first) {
            return listSize(first);
         }
      }, null),
      DOUBLE("double", 0, false, true, new OneArgFunctor() {
         @Override
         public Value exec(Value value) {
            return toDouble(value);
         }
      }, null),
      // GCD accepts actually *list* of values
      GREATEST_COMMON_DIVISOR("gcd", 0, false, true, new OneArgFunctor() {
         @Override
         public Value exec(Value value) {
            return gcd(value);
         }
      }, null),
      ;

      private static Map<String, Operator> symbolMap = new HashMap<String, Operator>();
      private String symbol;
      private int precedence;
      private boolean isWhite;
      private boolean isFunction;
      private OneArgFunctor functor1;
      private TwoArgFunctor functor2;

      static {
         for (Operator op : values()) {
            symbolMap.put(op.symbol, op);
         }
      }

      Operator(String symbol, int precedence, boolean isWhite, boolean isFunction, OneArgFunctor functor1, TwoArgFunctor functor2) {
         this.symbol = symbol;
         this.precedence = precedence;
         this.isWhite = isWhite;
         this.isFunction = isFunction;
         this.functor1 = functor1;
         this.functor2 = functor2;
      }

      /**
       * @return Symbols that don't belong to functions
       */
      public static String[] symbols() {
         Operator[] values = values();
         ArrayList<String> symbols = new ArrayList<>(values.length);
         for (int i = 0; i < values.length; ++i) {
            if (!values[i].isFunction()) {
               symbols.add(values[i].symbol);
            }
         }
         return symbols.toArray(new String[symbols.size()]);
      }

      public static Operator from(String symbol) {
         return symbolMap.get(symbol);
      }

      public int precedence() {
         return precedence;
      }

      public boolean isWhite() {
         return isWhite;
      }

      public boolean isFunction() {
         return isFunction;
      }

      public void exec(Stack<Value> operands) {
         if (functor1 != null) {
            operands.push(functor1.exec(operands.pop()));
         } else if (functor2 != null) {
            Value second = operands.pop();
            Value first = operands.pop();
            operands.push(functor2.exec(first, second));
         } else {
            throw new IllegalStateException("This operator cannot be executed.");
         }
      }
   }
}

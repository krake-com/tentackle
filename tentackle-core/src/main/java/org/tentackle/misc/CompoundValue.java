/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.misc;

import org.tentackle.common.BMoney;
import org.tentackle.common.DMoney;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.script.Script;
import org.tentackle.script.ScriptFactory;
import org.tentackle.script.ScriptRuntimeException;
import org.tentackle.script.ScriptVariable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.Date;
import java.util.Objects;
import java.util.StringTokenizer;


/**
 * A compound value.
 * <p>
 * Compount values are used to dynamically create objects from strings. Such objects are often
 * used as parameters for methods or annotations. They come in three different flavors:
 *
 * <ol>
 * <li>fixed constants: these are simple strings and will be treated as constants.</li>
 * <li>references: they start with a dollar followed by a reference in dot-notation relative to a given object or a
 * static method or field. If the dotted path contains an element with the first character in uppercase, the latter is assumed.
 * Boolean references may be preceeded by <code>"!"</code> to negate the result.</li>
 * <li>a script in a scripting language, for example Groovy or JRuby.</li>
 * </ol>
 *
 * Examples:
 * <pre>
 * "123" -&gt; constant
 * "$grandTotal" -&gt; object.getGrandTotal()
 * "!$customer.customerNoValid" -&gt; !object.getCustomer().isCustomerNoValid()
 * "$org.tentackle.misc.StringHelper.now" -&gt; org.tentackle.misc.StringHelper.now()
 * "#!groovy{ object.amount &gt; 200}"
 * </pre>
 *
 * If the object parameter in the constructor is not-null the script will get the
 * parameter "object" set ("@object" in Ruby).
 * <p>
 * Notice: <code>"#!{"</code> or just <code>"{"</code> may be used as a shorthand for <code>"#!groovy{"</code>.
 *
 * @author harald
 */
public class CompoundValue {

  /** script variable for the parent object. */
  public static final String VAR_OBJECT = "object";


  /**
   * The value type of strings.
   * <ul>
   * <li>{@link #CONSTANT}: a constant value (e.g.: <code>"123"</code>)</li>
   * <li>{@link #REFERENCE}: a reference-path relative to the annotated parent object in dot-notation (e.g.: <code>"$invoice.grantTotal"</code>)</li>
   * <li>{@link #STATIC_REFERENCE}: a reference to a static method (e.g.: <code>"$org.tentackle.misc.StringHelper.now"</code>)</li>
   * <li>{@link #SCRIPT}: a script (e.g.: <code>"#!groovy{ object.amount &lt; 200 }"</code>)</li>
   * </ul>
   *
   * Notice: a backslash quotes the special meaning of characters, for ex. <code>"\$string"</code> is treated as the constant "$string".<br>
   */
  public enum Type {
    /** a fixed value (constant) */
    CONSTANT,
    /** a reference relative to the parent object */
    REFERENCE,
    /** a reference to a static method of a class */
    STATIC_REFERENCE,
    /** a script */
    SCRIPT,
  }



  // configuration parameters
  private String str;                         // the original string
  private Class<?> clazz;                     // the value's type

  // derived from configuration
  private Type type;                          // the type
  private String constant;                    // the constant value string, null if none
  private Object constantValue;               // the cached constant value
  private String reference;                   // the reference, null if none
  private String staticClassName;             // the name of the static class reference
  private boolean negate;                     // true if negate the (boolean) reference
  private Script script;                      // the script, null if none
  private boolean scriptCached;               // use caching for script, if possible
  private boolean scriptExecutedThreadSafe;   // execute script thread safe



  /**
   * Creates and configures a compound value.
   * <p>
   * The <code>clazz</code> is required for {@link Type#CONSTANT}.
   * For {@link Type#SCRIPT} it is optional and if given, the returned value is
   * converted to the given type, if possible.
   *
   * @param str the string value
   * @param clazz the class of the value
   */
  public CompoundValue(String str, Class<?> clazz) {
    configure(str, clazz);
  }

  /**
   * Creates and configures a compound value.<br>
   *
   * @param str the string value
   */
  public CompoundValue(String str) {
    configure(str, null);
  }

  /**
   * Creates an unconfigured compound value
   */
  public CompoundValue() {
  }




  @Override
  public String toString() {
    return "[" + type + "] " + str;
  }


  /**
   * Returns whether script should be cached if possible.
   *
   * @return true if cached
   */
  public boolean isScriptCached() {
    return scriptCached;
  }

  /**
   * Sets whether script should be cached if possible.
   *
   * @param scriptCached true if script code is used more than once by the application
   */
  public void setScriptCached(boolean scriptCached) {
    this.scriptCached = scriptCached;
  }


  /**
   * Returns whether script must be executed in a thread-safe way.
   *
   * @return true if execute thread synchronized
   */
  public boolean isScriptExecutedThreadSafe() {
    return scriptExecutedThreadSafe;
  }

  /**
   * Sets whether script must be executed in a thread-safe way.
   *
   * @param scriptExecutedThreadSafe true if execute thread synchronized
   */
  public void setScriptExecutedThreadSafe(boolean scriptExecutedThreadSafe) {
    this.scriptExecutedThreadSafe = scriptExecutedThreadSafe;
  }



  /**
   * Configures or re-configures a compound value.
   * <p>
   * The <code>clazz</code> is required for {@link Type#CONSTANT}.
   * For {@link Type#SCRIPT} it is optional and if given, the returned value is
   * converted to the given type, if possible.
   *
   * @param str the string value
   * @param clazz the class of the value.
   */
  public void configure(String str, Class<?> clazz) {

    this.str   = str;
    this.clazz = clazz;

    // reset in case re-configured
    type              = null;
    constant          = null;
    constantValue     = null;
    reference         = null;
    staticClassName   = null;
    negate            = false;
    script            = null;

    int length = str == null ? 0 : str.length();

    if (length == 0) {
      throw new IllegalArgumentException("string value must not be null or empty");
    }

    char c = str.charAt(0);

    if (c == '\\') {
      if (length < 2) {
        throw new IllegalArgumentException("constant value must not be empty");
      }
      type = Type.CONSTANT;
      constant = str.substring(1);
      if (clazz == null) {
        throw new IllegalArgumentException("clazz must be set for type " + type);
      }
    }
    else {
      int ndx = str.indexOf('{');
      if ((ndx == 0 || ndx >= 2 && str.startsWith("#!")) && str.endsWith("}")) {
        type = Type.SCRIPT;
        try {
          // scripts are created threadsafe. This is a small overhead but is necessary
          // because compoundvalues are heavily used during validation which is by several threads
          // in parallel in servers.
          String language = str.substring(ndx == 0 ? 0 : 2, ndx);
          String code = str.substring(ndx + 1, length - 1);
          script = ScriptFactory.getInstance().createScript(language, code, isScriptCached(), isScriptExecutedThreadSafe());
        }
        catch (ScriptRuntimeException ex) {
          throw new IllegalArgumentException("creating script '" + str + "' failed", ex);
        }
      }
      else  {
        if (c == '$' || c == '!') {
          if (c == '$') {
            if (length < 2) {
              throw new IllegalArgumentException("reference must not be empty");
            }
            reference = str.substring(1);
          }
          else {  // == '!'
            if (length < 3) {
              throw new IllegalArgumentException("negated boolean reference must not be empty");
            }
            if (str.charAt(1) != '$') {
              throw new IllegalArgumentException("negated boolean reference must start with !$");
            }
            reference = str.substring(2);
            negate = true;
          }

          type = Type.REFERENCE;

          // check if reference is static
          StringTokenizer stok = new StringTokenizer(reference, ".");
          StringBuilder classBuf = new StringBuilder();
          while (stok.hasMoreTokens()) {
            String token = stok.nextToken();
            if (classBuf.length() > 0) {
              classBuf.append('.');
            }
            classBuf.append(token);
            if (Character.isUpperCase(token.charAt(0))) {
              // static reference
              type = Type.STATIC_REFERENCE;
              staticClassName = classBuf.toString();
              reference = reference.substring(staticClassName.length() + 1);
              break;
            }
          }
        }
        else  {
          type = Type.CONSTANT;
          constant = str;
        }
      }
    }
  }



  /**
   * Checks whether this compound value is configured.
   *
   * @return true if configured
   */
  public boolean isConfigured() {
    return getType() != null;
  }


  /**
   * Gets the value type.
   *
   * @return the value type
   */
  public Type getType() {
    return type;
  }


  /**
   * Gets the script.
   *
   * @return the script, null if none
   */
  public Script getScript() {
    return script;
  }


  /**
   * Gets the constant value.
   *
   * @return the constant value, null if none
   */
  public Object getConstantValue() {
    return constantValue;
  }


  /**
   * Gets the reference.
   *
   * @return the reference, null if none
   */
  public String getReference() {
    return reference;
  }

  /**
   * Gets the static classname.
   *
   * @return the static class name, null if none, i.e. not a static reference
   */
  public String getStaticClassName() {
    return staticClassName;
  }


  /**
   * Gets the logical negation flag.
   *
   * @return true boolean result will be negated
   */
  public boolean isNegate() {
    return negate;
  }


  /**
   * Gets the value according to the type.<br>
   * <p>
   * The <code>parentObject</code> is required for {@link Type#REFERENCE} and denotes the
   * object the reference refers to.<br>
   * For {@link Type#SCRIPT} it is optional and if given will set the script's variable
   * {@link #VAR_OBJECT}.
   *
   * @param parentObject the parent object
   * @param variables the script variables (only for scripts)
   * @return some value
   * @throws IllegalArgumentException if evaluating the value failed
   * @throws IllegalStateException if compound value is not configured
   */
  public Object getValue(Object parentObject, ScriptVariable... variables) {

    if (!isConfigured()) {
      throw new IllegalStateException("compound value is not configured");
    }

    switch (type) {

      case CONSTANT:
        if (constantValue == null) {
          // create it once
          constantValue = createConstantValue(clazz, constant);
        }
        return constantValue;

      case REFERENCE:
        if (parentObject == null) {
          throw new IllegalArgumentException("parent must not be null for type " + type);
        }
        try {
          Object value = ReflectionHelper.getValueByPath(parentObject, reference);
          return negate ? negateBoolean(value) : value;
        }
        catch (RuntimeException ex) {
          throw new IllegalArgumentException("cannot evaluate reference '" + reference + "'", ex);
        }

      case STATIC_REFERENCE:
        try {
          Class<?> staticClass = Class.forName(staticClassName);
          Object value = ReflectionHelper.getStaticValueByPath(staticClass, reference);
          return negate ? negateBoolean(value) : value;
        }
        catch (ClassNotFoundException e1) {
          throw new IllegalArgumentException("cannot evaluate static reference '" + staticClassName + "." + reference + "'", e1);
        }
        catch (RuntimeException e2) {
          throw new IllegalArgumentException("cannot evaluate reference '" + reference + "'", e2);
        }

      case SCRIPT:
        try {
          // always set the parent object
          ScriptVariable objectVariable = new ScriptVariable(VAR_OBJECT, parentObject);
          if (variables == null) {
            variables = new ScriptVariable[] { objectVariable };
          }
          else  {
            // add to array, the last one counts if "object" is already defined
            ScriptVariable[] newVariables = new ScriptVariable[variables.length + 1];
            System.arraycopy(variables, 0, newVariables, 0, variables.length);
            newVariables[variables.length] = objectVariable;
            variables = newVariables;
          }
          script.execute(variables);
          Object result = script.getResult();
          if (clazz != null && result != null && !(clazz.isAssignableFrom(result.getClass()))) {
            // convert if possible
            result = convertValue(clazz, result);
          }
          return result;
        }
        catch (RuntimeException ex) {
          throw new IllegalArgumentException("evaluating script '" + script + "' failed", ex);
        }

    }

    throw new IllegalArgumentException("unsupported validation value type");
  }



  /**
   * Gets the value according to the type.<br>
   *
   * @param variables the script variables (only for scripts)
   * @return some value
   * @throws IllegalArgumentException if evaluating the value failed
   * @throws IllegalStateException if compound value is not configured
   */
  public Object getValue(ScriptVariable... variables) {
    return getValue(null, variables);
  }



  /**
   * Parses the compound value.<br>
   * According to the type of the compound value the following tests will be done:
   *
   * <ul>
   * <li>{@link Type#CONSTANT}: the constant value will be evaluated from the value-string</li>
   * <li>{@link Type#REFERENCE} and {@link Type#STATIC_REFERENCE}: the reference will be parsed (but no value evaluated).
   *      See {@link ReflectionHelper#verifyPath(java.lang.Class, java.lang.String)} </li>
   * <li>{@link Type#SCRIPT}: the script will be parsed</li>
   * </ul>
   *
   * @param parentObjectClass for {@link Type#REFERENCE}
   * @throws IllegalArgumentException if parsing failed
   */
  public void parse(Class<?> parentObjectClass) {

    switch (type) {

      case CONSTANT:
        createConstantValue(clazz, constant);
        break;

      case REFERENCE:
        try {
          if (!ReflectionHelper.verifyPath(parentObjectClass, reference)) {
            throw new IllegalArgumentException("'" + parentObjectClass.getName() + "." + reference + "' is not a valid path");
          }
          break;
        }
        catch (RuntimeException ex) {
          throw new IllegalArgumentException("cannot parse reference '" + reference + "'", ex);
        }

      case STATIC_REFERENCE:
        try {
          Class<?> staticClass = Class.forName(staticClassName);
          if (!ReflectionHelper.verifyPath(staticClass, reference)) {
            throw new IllegalArgumentException("'" + staticClassName + "." + reference + "' is not a valid path");
          }
          break;
        }
        catch (ClassNotFoundException e1) {
          throw new IllegalArgumentException("cannot parse static reference '" + staticClassName + "." + reference + "'", e1);
        }
        catch (RuntimeException e2) {
          throw new IllegalArgumentException("cannot parse reference '" + reference + "'", e2);
        }

      case SCRIPT:
        try {
          script.parse();
          break;
        }
        catch (ScriptRuntimeException ex) {
          throw new IllegalArgumentException(ex);
        }

    }
  }


  /**
   * Negates a boolean value.
   *
   * @param value the value
   * @return the negated boolean
   */
  private Boolean negateBoolean(Object value) {
    if (value instanceof Boolean) {
      return !((Boolean) value);
    }
    else  {
      throw new IllegalArgumentException("value of " + this + " is not boolean");
    }
  }


  /**
   * Creates the comparable of the given type from the given string.
   *
   * @param clazz the type
   * @param value the value
   * @return the constant value
   */
  @SuppressWarnings("unchecked")
  private Object createConstantValue(Class<?> clazz, String value) {

    if (Objects.requireNonNull(clazz, "clazz").isPrimitive()) {
      clazz = ReflectionHelper.primitiveToWrapperClass(clazz);
    }

    if (Objects.requireNonNull(value, "value").isEmpty()) {
      throw new IllegalArgumentException("empty value");
    }

    try {
      if (clazz.isEnum()) {
        return Enum.valueOf((Class<Enum>) clazz, value);
      }
      else if (DMoney.class.isAssignableFrom(clazz)) {
        return new DMoney(value, FormatHelper.determineScale(value));
      }
      else if (BMoney.class.isAssignableFrom(clazz)) {
        return new BMoney(value, FormatHelper.determineScale(value));
      }
      else if (BigDecimal.class.isAssignableFrom(clazz)) {
        return new BigDecimal(value);
      }
      else if (BigInteger.class.isAssignableFrom(clazz)) {
        return new BigInteger(value);
      }
      else if (Double.class == clazz) {
        return Double.valueOf(value);
      }
      else if (Float.class == clazz) {
        return Float.valueOf(value);
      }
      else if (Long.class == clazz) {
        return Long.valueOf(value);
      }
      else if (Integer.class == clazz) {
        return Integer.valueOf(value);
      }
      else if (Short.class == clazz) {
        return Short.valueOf(value);
      }
      else if (Byte.class == clazz) {
        return Byte.valueOf(value);
      }
      else if (Character.class == clazz) {
        return value.charAt(0);
      }
      else if (String.class == clazz) {
        return value;
      }
      else if (Boolean.class == clazz) {
        return Boolean.valueOf(value);
      }
      else if (Timestamp.class.isAssignableFrom(clazz)) {
        return FormatHelper.parseTimestamp(value);
      }
      else if (Time.class.isAssignableFrom(clazz)) {
        return FormatHelper.parseTime(value);
      }
      else if (Date.class.isAssignableFrom(clazz)) {
        return FormatHelper.parseDate(value);
      }
    }
    catch (NumberFormatException | ParseException ex) {
      throw new IllegalArgumentException("cannot create " + clazz.getName() + " from '" + value + "'", ex);
    }

    throw new IllegalArgumentException("unsupported type: " + clazz.getName());
  }


  /**
   * Converts the value to the given type, if possible.
   *
   * @param clazz the type
   * @param value the value
   * @return the converted value
   */
  @SuppressWarnings("unchecked")
  private Object convertValue(Class<?> clazz, Object value) {

    try {
      if (clazz.isEnum() && value instanceof String) {
        return Enum.valueOf((Class<Enum>) clazz, (String) value);
      }
      if (DMoney.class.isAssignableFrom(clazz)) {
        if (value instanceof DMoney) {
          return value;
        }
        else  {
          if (value instanceof BigDecimal) {
            return new DMoney((BigDecimal) value);
          }
          else if (value instanceof String) {
            return new DMoney((String) value, FormatHelper.determineScale((String) value));
          }
        }
      }
      if (BMoney.class.isAssignableFrom(clazz)) {
        if (value instanceof BMoney) {
          return value;
        }
        else  {
          if (value instanceof BigDecimal) {
            return new BMoney((BigDecimal) value);
          }
          else if (value instanceof String) {
            return new BMoney((String) value, FormatHelper.determineScale((String) value));
          }
        }
      }
      else if (BigDecimal.class.isAssignableFrom(clazz)) {
        if (value instanceof BigDecimal) {
          return value;
        }
        else if (value instanceof String) {
          return new BMoney((String) value, FormatHelper.determineScale((String) value));
        }
      }
      else if (BigInteger.class.isAssignableFrom(clazz)) {
        if (value instanceof BigInteger) {
          return value;
        }
        else  {
          if (value instanceof Number) {
            return BigInteger.valueOf(((Number) value).longValue());
          }
          else if (value instanceof String) {
            return new BigInteger((String) value);
          }
        }
      }
      else if (Double.class == clazz) {
        if (value instanceof Double) {
          return value;
        }
        else  {
          if (value instanceof Number) {
            return ((Number) value).doubleValue();
          }
          else if (value instanceof String) {
            return Double.parseDouble((String) value);
          }
        }
      }
      else if (Float.class == clazz) {
        if (value instanceof Float) {
          return value;
        }
        else  {
          if (value instanceof Number) {
            return ((Number) value).floatValue();
          }
          else if (value instanceof String) {
            return Float.parseFloat((String) value);
          }
        }
      }
      else if (Long.class == clazz) {
        if (value instanceof Long) {
          return value;
        }
        else  {
          if (value instanceof Number) {
            return ((Number) value).longValue();
          }
          else if (value instanceof String) {
            return Long.parseLong((String) value);
          }
        }
      }
      else if (Integer.class == clazz) {
        if (value instanceof Integer) {
          return value;
        }
        else  {
          if (value instanceof Number) {
            return ((Number) value).intValue();
          }
          else if (value instanceof String) {
            return Integer.parseInt((String) value);
          }
        }
      }
      else if (Short.class == clazz) {
        if (value instanceof Short) {
          return value;
        }
        else  {
          if (value instanceof Number) {
            return ((Number) value).shortValue();
          }
          else if (value instanceof String) {
            return Short.parseShort((String) value);
          }
        }
      }
      else if (Byte.class == clazz) {
        if (value instanceof Byte) {
          return value;
        }
        else  {
          if (value instanceof Number) {
            return ((Number) value).byteValue();
          }
          else if (value instanceof String) {
            return Byte.parseByte((String) value);
          }
        }
      }
      else if (Character.class == clazz) {
        if (value instanceof Character) {
          return value;
        }
      }
      else if (String.class == clazz) {
        if (value instanceof Character) {
          return value;
        }
        else  {
          return value.toString();
        }
      }
      else if (Boolean.class == clazz) {
        if (value instanceof Boolean) {
          return value;
        }
        else if (value instanceof String) {
          return Boolean.valueOf((String) value);
        }
      }
      else if (Timestamp.class.isAssignableFrom(clazz)) {
        if (value instanceof Timestamp) {
          return value;
        }
        else if (value instanceof String) {
          return FormatHelper.parseTimestamp(((String) value));
        }
      }
      else if (Time.class.isAssignableFrom(clazz)) {
        if (value instanceof Time) {
          return value;
        }
        else if (value instanceof String) {
          return FormatHelper.parseTime(((String) value));
        }
      }
      else if (Date.class.isAssignableFrom(clazz)) {
        if (value instanceof Date) {
          return value;
        }
        else if (value instanceof String) {
          return FormatHelper.parseDate(((String) value));
        }
      }
    }
    catch (NumberFormatException | ParseException ex) {
      throw new IllegalArgumentException("conversion failed", ex);
    }

    throw new IllegalArgumentException("unsupported conversion: " + value.getClass().getName() + " -> "+ clazz.getName());
  }

}

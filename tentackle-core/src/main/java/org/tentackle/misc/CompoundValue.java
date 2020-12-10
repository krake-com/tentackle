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

import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.script.Script;
import org.tentackle.script.ScriptConverter;
import org.tentackle.script.ScriptFactory;
import org.tentackle.script.ScriptRuntimeException;
import org.tentackle.script.ScriptVariable;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Function;


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



  private final String text;                                                  // the value source text
  private final Class<?> clazz;                                               // the value's type
  private final Function<String, ScriptConverter> scriptConverterProvider;    // the optional script converter provider
  private final boolean scriptCached;                                         // use caching for script, if possible
  private final boolean scriptThreadSafe;                                     // execute script thread safe

  private Type type;                                                          // the type
  private String constant;                                                    // the constant value string, null if none
  private Object constantValue;                                               // the cached constant value
  private String reference;                                                   // the reference, null if none
  private String staticClassName;                                             // the name of the static class reference
  private boolean negate;                                                     // true if negate the (boolean) reference
  private Script script;                                                      // the script, null if none



  /**
   * Creates and configures a compound value.
   * <p>
   * The <code>clazz</code> is required for {@link Type#CONSTANT}.
   * For {@link Type#SCRIPT} it is optional and if given, the returned value is
   * converted to the given type, if possible.
   * <p>
   * The parameters <code>scriptConverterProviderscriptCached</code>, <code>scriptCached</code> and <code>scriptThreadSafe</code>
   * are for {@link Type#SCRIPT} only.
   *
   * @param text the string value
   * @param clazz the class of the value
   * @param scriptCached true if use caching if possible because script code may used more than once
   * @param scriptThreadSafe true if the script may be invoked in parallel from different threads
   * @param scriptConverterProvider the optional provider for the script converter
   */
  public CompoundValue(String text, Class<?> clazz, boolean scriptCached, boolean scriptThreadSafe, Function<String, ScriptConverter> scriptConverterProvider) {
    this.text = text;
    this.clazz = clazz;
    this.scriptConverterProvider = scriptConverterProvider;
    this.scriptCached = scriptCached;
    this.scriptThreadSafe = scriptThreadSafe;

    // reset in case re-configured
    type              = null;
    constant          = null;
    constantValue     = null;
    reference         = null;
    staticClassName   = null;
    negate            = false;
    script            = null;

    int length = text == null ? 0 : text.length();

    if (length == 0) {
      throw new IllegalArgumentException("string value must not be null or empty");
    }

    char c = text.charAt(0);

    if (c == '\\') {
      if (length < 2) {
        throw new IllegalArgumentException("constant value must not be empty");
      }
      type = Type.CONSTANT;
      constant = text.substring(1);
      if (clazz == null) {
        throw new IllegalArgumentException("clazz must be set for type " + type);
      }
    }
    else {
      int ndx = text.indexOf('{');
      if ((ndx == 0 || ndx >= 2 && text.startsWith("#!")) && text.endsWith("}")) {
        type = Type.SCRIPT;
        try {
          String language = text.substring(ndx == 0 ? 0 : 2, ndx);
          String code = text.substring(ndx + 1, length - 1);
          script = ScriptFactory.getInstance().createScript(language, code, isScriptCached(), isScriptThreadSafe(), getScriptConverterProvider());
        }
        catch (ScriptRuntimeException ex) {
          throw new IllegalArgumentException("creating script '" + text + "' failed", ex);
        }
      }
      else  {
        if (c == '$' || c == '!') {
          if (c == '$') {
            if (length < 2) {
              throw new IllegalArgumentException("reference must not be empty");
            }
            reference = text.substring(1);
          }
          else {  // == '!'
            if (length < 3) {
              throw new IllegalArgumentException("negated boolean reference must not be empty");
            }
            if (text.charAt(1) != '$') {
              throw new IllegalArgumentException("negated boolean reference must start with !$");
            }
            reference = text.substring(2);
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
          constant = text;
        }
      }
    }
  }

  /**
   * Creates and configures a compound value.
   * <p>
   * The <code>clazz</code> is required for {@link Type#CONSTANT}.
   * For {@link Type#SCRIPT} it is optional and if given, the returned value is
   * converted to the given type, if possible.
   *
   * @param text the string value
   * @param clazz the class of the value
   */
  public CompoundValue(String text, Class<?> clazz) {
    this(text, clazz, false, false, null);
  }

  /**
   * Creates and configures a compound value.<br>
   *
   * @param text the string value
   */
  public CompoundValue(String text) {
    this(text, null, false, false, null);
  }


  @Override
  public String toString() {
    return "[" + type + "] " + text;
  }

  /**
   * Gets the source text.
   *
   * @return the string value
   */
  public String getText() {
    return text;
  }

  /**
   * Gets the class of the value.
   *
   * @return the value's class, null if determined by SCRIPT or not necessary for this {@link Type}
   */
  public Class<?> getClazz() {
    return clazz;
  }

  /**
   * Gets the provider for the script converter that will translate the script before being parsed.
   *
   * @return the provider, null if none
   */
  public Function<String, ScriptConverter> getScriptConverterProvider() {
    return scriptConverterProvider;
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
   * Returns whether script must be executed in a thread-safe manner.
   *
   * @return true if script may be invoked from more than one thread
   */
  public boolean isScriptThreadSafe() {
    return scriptThreadSafe;
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
   * {@link ScriptVariable#VAR_OBJECT}.
   * <p>
   * Throws {@link IllegalArgumentException} if evaluating the value failed.
   *
   * @param parentObject the parent object, null if none
   * @param variables the script variables (only for scripts), null or empty if none
   * @return some value
   */
  public Object getValue(Object parentObject, Set<ScriptVariable> variables) {

    switch (type) {

      case CONSTANT:
        if (constantValue == null) {
          // create it once
          constantValue = ObjectUtilities.getInstance().convert(clazz, constant);
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
        catch (ApplicationException | RuntimeException ex) {
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
        catch (ApplicationException | RuntimeException e2) {
          throw new IllegalArgumentException("cannot evaluate reference '" + reference + "'", e2);
        }

      case SCRIPT:
        try {
          if (parentObject != null) {
            ScriptVariable parentVariable = new ScriptVariable(ScriptVariable.VAR_OBJECT, parentObject);
            if (variables == null) {
              variables = new HashSet<>();
              variables.add(parentVariable);
            }
            else {
              try {
                variables.add(parentVariable);    // add or replace if already present
              }
              catch (RuntimeException rx) {       // immutable set?
                variables = new HashSet<>(variables);
                variables.add(parentVariable);
              }
            }
          }
          Object result = script.execute(variables);
          if (clazz != null) {
            // convert if necessary
            result = ObjectUtilities.getInstance().convert(clazz, result);
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
   * <p>
   * The <code>parentObject</code> is required for {@link Type#REFERENCE} and denotes the
   * object the reference refers to.<br>
   * For {@link Type#SCRIPT} it is optional and if given will set the script's variable
   * {@link ScriptVariable#VAR_OBJECT}.
   * <p>
   * Throws {@link IllegalArgumentException} if evaluating the value failed
   * or if there are any duplicate variable names.
   *
   * @param parentObject the parent object
   * @param variables the optional script variables (only for scripts)
   * @return some value
   */
  public Object getValue(Object parentObject, ScriptVariable... variables) {
    Set<ScriptVariable> variableSet;
    if (variables != null && variables.length > 0) {
      variableSet = new HashSet<>();
      for (ScriptVariable variable: variables) {
        if (!variableSet.add(variable)) {
          throw new IllegalArgumentException("duplicate variable: " + variable);
        }
      }
    }
    else {
      variableSet = null;
    }
    return getValue(parentObject, variableSet);
  }

  /**
   * Gets the value according to the type.
   * <p>
   * Throws {@link IllegalArgumentException} if evaluating the value failed.
   *
   * @param variables the script variables (only for scripts), null or empty if none
   * @return some value
   */
  public Object getValue(Set<ScriptVariable> variables) {
    return getValue(null, variables);
  }

  /**
   * Gets the value according to the type.
   * <p>
   * Throws {@link IllegalArgumentException} if evaluating the value failed
   * or if there are any duplicate variable names.
   *
   * @param variables the optional script variables (only for scripts)
   * @return some value
   */
  public Object getValue(ScriptVariable... variables) {
    return getValue(null, variables);
  }

  /**
   * Validates the configuration of this compoundvalue.<br>
   * Validates the script, if any.
   */
  public void validate() {
    if (script != null) {
      script.validate();
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

}

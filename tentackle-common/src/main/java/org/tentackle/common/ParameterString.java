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

package org.tentackle.common;

import java.io.Serial;
import java.io.Serializable;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * A string of parameters.
 * <p>
 * Holds parameters of the form:
 * <pre>
 * name1='value1', name2='value2', ...
 * </pre>
 * Where name must be a valid name according to the Java convention for variables.
 * The values are any kind of strings enclosed in single quotes.
 * If the value contains a single quote, it is encoded as two consecutive single quotes.
 * The parameters are separated by a comma.
 * The empty string is encoded as nameX instead of nameX='' as a convenience to denote
 * just the presence of a name (e.g., for booleans).
 * <p>
 * The parser is lenient: any characters between a name and its quoted value (such as {@code =} or {@code :})
 * and between the parameters are ignored. A name without a value ends at the next comma or whitespace.
 * The insertion order of the parameters is preserved.
 *
 * @author harald
 */
public class ParameterString implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** the name/value pairs. */
  @SuppressWarnings("serial")
  private final Map<String,String> parameters;


  /**
   * Creates an empty parameter string.
   */
  public ParameterString() {
    parameters = new LinkedHashMap<>();
  }

  /**
   * Creates a parameter string from its string representation.
   *
   * @param parameterString the parameter string
   * @throws ParseException if parsing the string failed
   */
  public ParameterString(String parameterString) throws ParseException {
    this();
    parse(parameterString);
  }


  /**
   * Sets a parameter.
   *
   * @param name the name of the parameter
   * @param value the value, null to remove
   * @return the old value if replaced, null if new
   */
  public String setParameter(String name, String value) {
    if (value == null) {
      return parameters.remove(name);
    }
    return parameters.put(name, value);
  }


  /**
   * Gets the value of a parameter.
   *
   * @param name the parameter name
   * @return the value, null if no such parameter
   */
  public String getParameter(String name) {
    return parameters.get(name);
  }


  /**
   * Gets the parameter names.<br>
   *
   * @return the parameter names
   */
  public String[] getParameterNames() {
    return parameters.keySet().toArray(new String[0]);
  }


  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    for (Map.Entry<String,String> entry: parameters.entrySet()) {
      if (!buf.isEmpty()) {
        buf.append(", ");
      }
      buf.append(entry.getKey());
      if (!"".equals(entry.getValue())) {
        buf.append("='");
        buf.append(encodeValue(entry.getValue()));
        buf.append("'");
      }
    }
    return buf.toString();
  }

  /**
   * Converts a parameter string to a property list.
   *
   * @return the property list
   */
  public Properties toProperties() {
    Properties props = new Properties();
    for (Map.Entry<String,String> entry: parameters.entrySet()) {
      props.setProperty(entry.getKey(), entry.getValue());
    }
    return props;
  }


  /**
   * Parses the external string representation.<br>
   * Any parameters already held by this object are removed first.
   *
   * @param parameterString the parameter string, null to just clear the parameters
   * @throws ParseException if parsing the string failed
   */
  public void parse(String parameterString) throws ParseException {
    parameters.clear();
    if (parameterString != null) {
      StringBuilder nameBuf = null;
      StringBuilder valueBuf = null;
      int valueStart = 0;
      int len = parameterString.length();
      for (int pos = 0; pos < len; pos++) {
        char c = parameterString.charAt(pos);
        if (nameBuf == null) {
          if (Character.isJavaIdentifierStart(c)) {
            // starting a new parameter name
            nameBuf = new StringBuilder();
            nameBuf.append(c);
          }
          // else ignore syntactic sugar
        }
        else {
          if (valueBuf == null) {
            // still reading the name
            if (Character.isJavaIdentifierPart(c)) {
              nameBuf.append(c);
            }
            else  {
              if (c == '\'') {
                // starting a new value
                valueBuf = new StringBuilder();
                valueStart = pos;
              }
              else if (c == ',' || Character.isWhitespace(c)) {
                // end of a name without value: store parameter
                parameters.put(nameBuf.toString(), "");
                nameBuf = null;
              }
              // else ignore syntactic sugar like '=' or ':' etc...
            }
          }
          else  {
            // still reading the value
            if (c == '\'') {
              // check if quoted
              if (pos < len - 1 && parameterString.charAt(pos + 1) == '\'') {
                pos++;
                valueBuf.append(c);
              }
              else  {
                // end of value: store parameter
                parameters.put(nameBuf.toString(), valueBuf.toString());
                nameBuf = null;
                valueBuf = null;
              }
            }
            else  {
              valueBuf.append(c);
            }
          }
        }
      }
      if (valueBuf != null) {
        throw new ParseException("malformed parameter string at value " + nameBuf + "=" + valueBuf, valueStart);
      }
      if (nameBuf != null) {
        // last name was without =...
        parameters.put(nameBuf.toString(), "");
      }
    }
  }


  /**
   * Encodes a value for external representation.
   *
   * @param value the internal value
   * @return the external string representation
   */
  private String encodeValue(String value) {
    return value.replace("'", "''");
  }

}
